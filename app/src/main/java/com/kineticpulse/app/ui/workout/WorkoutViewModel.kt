package com.kineticpulse.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kineticpulse.app.ai.EngineState
import com.kineticpulse.app.ai.GemmaLocalManager
import com.kineticpulse.app.data.db.SetSource
import com.kineticpulse.app.data.db.SetWithExercise
import com.kineticpulse.app.data.repository.WorkoutRepository
import com.kineticpulse.core.model.ExerciseModality
import com.kineticpulse.core.model.RoutineSetDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickLogUiState(
    val text: String = "",
    val isParsing: Boolean = false,
    val preview: List<RoutineSetDto> = emptyList(),
)

/** Entreno en curso. */
data class ActiveSession(val id: Long, val startedAt: Long)

/** Resumen que se enseña en Inicio justo después de terminar. */
data class SessionSummary(val sets: Int, val minutes: Int)

/** Mensajes de un solo uso para el snackbar. */
sealed interface WorkoutEvent {
    data class SetSaved(val count: Int) : WorkoutEvent
    data object SetUndone : WorkoutEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val gemma: GemmaLocalManager,
) : ViewModel(), WorkoutActions {

    private val session = MutableStateFlow<ActiveSession?>(null)
    private val draft = MutableStateFlow(LiveSetDraft())
    private val quickLog = MutableStateFlow(QuickLogUiState())
    private val lastSetAt = MutableStateFlow<Long?>(null)
    private val lastSummary = MutableStateFlow<SessionSummary?>(null)
    private val _events = Channel<WorkoutEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutEvent> = _events.receiveAsFlow()

    private val sets: Flow<List<SetWithExercise>> = session.flatMapLatest { s ->
        s?.let { repository.observeSessionSets(it.id) } ?: flowOf(emptyList())
    }

    private val exercises: Flow<List<ExerciseOption>> = repository.observeExercises().map { saved ->
        val fromDb = saved.map { ExerciseOption(it.name, it.modality) }
        val known = fromDb.map { it.name }.toSet()
        fromDb + DEFAULT_EXERCISES.filter { it.name !in known }
    }

    private val home: Flow<HomeUiState> = combine(
        repository.observeWeekSessionCount(),
        repository.observeFatigue(),
        lastSummary,
    ) { week, fatigue, summary -> HomeUiState(week, fatigue?.level, summary) }

    private val active: Flow<ActiveUiState?> = combine(
        session,
        draft,
        sets,
        lastSetAt,
        quickLog,
    ) { s, d, list, rest, ql ->
        s?.let { ActiveUiState(it.startedAt, d, list, rest, ql) }
    }

    val uiState: StateFlow<WorkoutUiState> = combine(home, active, exercises) { h, a, ex ->
        WorkoutUiState(isLoading = false, home = h, active = a, exercises = ex)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    init {
        // Si la app se cerró a mitad de entreno, lo retomamos tal cual.
        viewModelScope.launch {
            repository.resumableSession()?.let { open ->
                session.value = ActiveSession(open.id, open.startedAt)
                prefillFromHistory(draft.value.exercise, draft.value.modality)
            }
        }
        // Precarga en segundo plano: cuando el usuario escriba la primera nota el modelo ya está listo.
        gemma.loadAsync()
    }

    override fun onStartWorkout() {
        if (session.value != null) return
        viewModelScope.launch {
            val startedAt = repository.now()
            val id = repository.startSession()
            lastSummary.value = null
            lastSetAt.value = null
            session.value = ActiveSession(id, startedAt)
            prefillFromHistory(draft.value.exercise, draft.value.modality)
        }
    }

    override fun onFinishWorkout() {
        val current = session.value ?: return
        viewModelScope.launch {
            val count = uiState.value.active?.sets?.size ?: 0
            repository.finishSession(current.id)
            val minutes = ((repository.now() - current.startedAt) / 60_000).toInt()
            lastSummary.value = SessionSummary(count, minutes).takeIf { count > 0 }
            session.value = null
            quickLog.value = QuickLogUiState()
            draft.update { it.copy(setNumber = 1) }
        }
    }

    override fun onDraftChange(draft: LiveSetDraft) = this.draft.update { draft }

    override fun onPickExercise(option: ExerciseOption) {
        draft.update { it.copy(exercise = option.name.trim().lowercase(), modality = option.modality) }
        viewModelScope.launch { prefillFromHistory(option.name, option.modality) }
    }

    override fun onLogSet(draft: LiveSetDraft) {
        val id = session.value?.id ?: return
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
            lastSetAt.value = repository.now()
            this@WorkoutViewModel.draft.update { it.copy(setNumber = it.setNumber + 1) }
            _events.send(WorkoutEvent.SetSaved(1))
        }
    }

    override fun onUndoLastSet() {
        val id = session.value?.id ?: return
        viewModelScope.launch {
            repository.deleteLastSet(id)
            draft.update { it.copy(setNumber = (it.setNumber - 1).coerceAtLeast(1)) }
            _events.send(WorkoutEvent.SetUndone)
        }
    }

    override fun onQuickLogTextChange(text: String) = quickLog.update { it.copy(text = text) }

    /** Procesa la nota libre; la vista previa se rellena set a set mientras el modelo genera. */
    override fun onQuickLogSubmit() {
        val id = session.value?.id ?: return
        val input = quickLog.value.text.trim().takeIf { it.isNotEmpty() } ?: return
        if (quickLog.value.isParsing) return
        val source = if (gemma.state.value is EngineState.Ready) SetSource.QUICK_LOG_AI else SetSource.QUICK_LOG_HEURISTIC

        viewModelScope.launch {
            quickLog.update { it.copy(isParsing = true, preview = emptyList()) }
            val parsed = mutableListOf<RoutineSetDto>()
            try {
                gemma.parseQuickLog(input).collect { dto ->
                    parsed += dto
                    quickLog.update { it.copy(preview = parsed.toList()) }
                }
                repository.logSets(id, parsed, source)
                parsed.lastOrNull()?.let { last ->
                    draft.update {
                        it.copy(
                            exercise = last.exercise,
                            modality = last.modality,
                            weightKg = last.weightKg ?: it.weightKg,
                            reps = last.reps ?: it.reps,
                            holdSeconds = last.holdSeconds ?: it.holdSeconds,
                            rpe = last.rpe ?: it.rpe,
                        )
                    }
                    lastSetAt.value = repository.now()
                }
                quickLog.value = QuickLogUiState()
                val total = parsed.sumOf { it.sets }
                if (total > 0) _events.send(WorkoutEvent.SetSaved(total))
            } finally {
                quickLog.update { it.copy(isParsing = false) }
            }
        }
    }

    /** Propone el peso y las reps de la última vez que hiciste este ejercicio. */
    private suspend fun prefillFromHistory(exercise: String, modality: ExerciseModality) {
        val last = repository.lastSetFor(exercise)
        draft.update { d ->
            if (d.exercise != exercise.trim().lowercase()) return@update d
            val nextSet = (uiState.value.active?.sets?.count { it.exerciseName == d.exercise } ?: 0) + 1
            if (last == null) {
                d.copy(modality = modality, setNumber = nextSet)
            } else {
                d.copy(
                    modality = modality,
                    weightKg = last.weightKg ?: d.weightKg,
                    reps = last.reps ?: d.reps,
                    holdSeconds = last.holdSeconds ?: d.holdSeconds,
                    rpe = last.rpe?.let { Effort.nearest(it).rpe } ?: d.rpe,
                    setNumber = nextSet,
                    previous = PreviousSet(last.weightKg, last.reps, last.holdSeconds),
                )
            }
        }
    }

    private companion object {
        /** Sugerencias para el primer uso; en cuanto registras algo, tus ejercicios salen primero. */
        val DEFAULT_EXERCISES = listOf(
            ExerciseOption("press banca", ExerciseModality.WEIGHTED),
            ExerciseOption("sentadilla", ExerciseModality.WEIGHTED),
            ExerciseOption("peso muerto", ExerciseModality.WEIGHTED),
            ExerciseOption("press militar", ExerciseModality.WEIGHTED),
            ExerciseOption("remo con barra", ExerciseModality.WEIGHTED),
            ExerciseOption("dominadas", ExerciseModality.BODYWEIGHT),
            ExerciseOption("fondos", ExerciseModality.BODYWEIGHT),
            ExerciseOption("flexiones", ExerciseModality.BODYWEIGHT),
            ExerciseOption("plancha", ExerciseModality.ISOMETRIC),
            ExerciseOption("planche lean", ExerciseModality.ISOMETRIC),
            ExerciseOption("l-sit", ExerciseModality.ISOMETRIC),
        )
    }
}
