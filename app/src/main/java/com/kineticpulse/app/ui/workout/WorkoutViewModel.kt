package com.kineticpulse.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kineticpulse.app.ai.EngineState
import com.kineticpulse.app.ai.GemmaLocalManager
import com.kineticpulse.app.data.db.SetSource
import com.kineticpulse.app.data.db.SetWithExercise
import com.kineticpulse.app.data.repository.WorkoutRepository
import com.kineticpulse.core.fatigue.FatigueReport
import com.kineticpulse.core.model.ExerciseModality
import com.kineticpulse.core.model.RoutineSetDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickLogUiState(
    val text: String = "",
    val isParsing: Boolean = false,
    val preview: List<RoutineSetDto> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val gemma: GemmaLocalManager,
) : ViewModel() {

    private val sessionId = MutableStateFlow<Long?>(null)

    private val _draft = MutableStateFlow(LiveSetDraft())
    val draft: StateFlow<LiveSetDraft> = _draft.asStateFlow()

    private val _quickLog = MutableStateFlow(QuickLogUiState())
    val quickLog: StateFlow<QuickLogUiState> = _quickLog.asStateFlow()

    val engineState: StateFlow<EngineState> = gemma.state

    val sets: StateFlow<List<SetWithExercise>> = sessionId
        .flatMapLatest { id -> id?.let(repository::observeSessionSets) ?: emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fatigue: StateFlow<FatigueReport?> = repository.observeFatigue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { sessionId.value = repository.startSession() }
        // Precarga en segundo plano: cuando el usuario escriba la primera nota el modelo ya está en VRAM.
        gemma.loadAsync()
    }

    fun onDraftChange(draft: LiveSetDraft) = _draft.update { draft }

    fun onLogSet(draft: LiveSetDraft) {
        val id = sessionId.value ?: return
        val dto = RoutineSetDto(
            exercise = draft.exercise,
            modality = draft.modality,
            sets = 1,
            reps = draft.reps.takeIf { draft.modality != ExerciseModality.ISOMETRIC },
            weightKg = draft.weightKg.takeIf { draft.modality == ExerciseModality.WEIGHTED },
            holdSeconds = draft.holdSeconds.takeIf { draft.modality == ExerciseModality.ISOMETRIC },
            rpe = draft.rpe,
        ).sanitized()
        viewModelScope.launch {
            repository.logSets(id, listOf(dto), SetSource.MANUAL)
            _draft.update { it.copy(setNumber = it.setNumber + 1) }
        }
    }

    fun onQuickLogTextChange(text: String) = _quickLog.update { it.copy(text = text) }

    /** Procesa la nota libre; la vista previa se rellena set a set mientras el modelo genera. */
    fun onQuickLogSubmit() {
        val id = sessionId.value ?: return
        val input = _quickLog.value.text.trim().takeIf { it.isNotEmpty() } ?: return
        val source = if (gemma.state.value is EngineState.Ready) SetSource.QUICK_LOG_AI else SetSource.QUICK_LOG_HEURISTIC

        viewModelScope.launch {
            _quickLog.update { it.copy(isParsing = true, preview = emptyList()) }
            val parsed = mutableListOf<RoutineSetDto>()
            try {
                gemma.parseQuickLog(input).collect { dto ->
                    parsed += dto
                    _quickLog.update { it.copy(preview = parsed.toList()) }
                }
                repository.logSets(id, parsed, source)
                parsed.lastOrNull()?.let { last ->
                    _draft.update {
                        it.copy(
                            exercise = last.exercise,
                            modality = last.modality,
                            weightKg = last.weightKg ?: it.weightKg,
                            reps = last.reps ?: it.reps,
                            holdSeconds = last.holdSeconds ?: it.holdSeconds,
                            rpe = last.rpe ?: it.rpe,
                            setNumber = 1,
                        )
                    }
                }
                _quickLog.update { QuickLogUiState(preview = parsed.toList()) }
            } finally {
                _quickLog.update { it.copy(isParsing = false) }
            }
        }
    }

    override fun onCleared() {
        // viewModelScope ya está cancelado aquí: el repositorio usa su propio scope de aplicación.
        sessionId.value?.let(repository::finishSessionAsync)
    }
}
