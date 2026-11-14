package com.kineticpulse.app.ui.workout

import androidx.annotation.StringRes
import com.kineticpulse.app.R
import com.kineticpulse.app.data.db.SetWithExercise
import com.kineticpulse.core.fatigue.FatigueLevel
import com.kineticpulse.core.model.ExerciseModality
import java.util.Locale
import kotlin.math.abs

/**
 * Toda la app es una sola pantalla con dos estados:
 *  - Inicio ([active] null): un botón grande para empezar.
 *  - Entreno ([active] no null): elegir ejercicio, ajustar números y guardar la serie.
 */
data class WorkoutUiState(
    val isLoading: Boolean = true,
    val home: HomeUiState = HomeUiState(),
    val active: ActiveUiState? = null,
    val exercises: List<ExerciseOption> = emptyList(),
)

data class HomeUiState(
    val sessionsThisWeek: Int = 0,
    val fatigue: FatigueLevel? = null,
    val lastSummary: SessionSummary? = null,
)

data class ActiveUiState(
    val startedAt: Long,
    val draft: LiveSetDraft,
    val sets: List<SetWithExercise>,
    val lastSetAt: Long?,
    val quickLog: QuickLogUiState,
)

data class ExerciseOption(val name: String, val modality: ExerciseModality)

/** Lo que hiciste la última vez en este ejercicio, para saber qué superar. */
data class PreviousSet(val weightKg: Double?, val reps: Int?, val holdSeconds: Int?)

/** Estado inmutable de la serie que se está preparando. La lógica vive en el ViewModel. */
data class LiveSetDraft(
    val exercise: String = "press banca",
    val modality: ExerciseModality = ExerciseModality.WEIGHTED,
    val weightKg: Double = 60.0,
    val reps: Int = 8,
    val holdSeconds: Int = 20,
    val rpe: Double = Effort.HARD.rpe,
    val setNumber: Int = 1,
    val previous: PreviousSet? = null,
)

/**
 * El RPE de 1 a 10 es jerga de entrenador. En pantalla se elige entre tres palabras y
 * por debajo se guarda el RPE equivalente, así el análisis de fatiga sigue funcionando.
 */
enum class Effort(val rpe: Double, @StringRes val label: Int) {
    EASY(7.0, R.string.effort_easy),
    HARD(8.5, R.string.effort_hard),
    MAX(10.0, R.string.effort_max),
    ;

    companion object {
        fun nearest(rpe: Double): Effort = entries.minBy { abs(it.rpe - rpe) }
    }
}

/** Callbacks de la pantalla. Interfaz para que las vistas previas pasen un objeto vacío. */
interface WorkoutActions {
    fun onStartWorkout() {}
    fun onFinishWorkout() {}
    fun onDraftChange(draft: LiveSetDraft) {}
    fun onPickExercise(option: ExerciseOption) {}
    fun onLogSet(draft: LiveSetDraft) {}
    fun onUndoLastSet() {}
    fun onQuickLogTextChange(text: String) {}
    fun onQuickLogSubmit() {}
}

object NoWorkoutActions : WorkoutActions

@StringRes
fun ExerciseModality.label(): Int = when (this) {
    ExerciseModality.WEIGHTED -> R.string.modality_weighted
    ExerciseModality.BODYWEIGHT -> R.string.modality_bodyweight
    ExerciseModality.ISOMETRIC -> R.string.modality_isometric
}

/** "press banca" → "Press banca". Los nombres se guardan en minúscula. */
fun String.displayName(): String = replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** 60.0 → "60", 62.5 → "62,5". */
fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toLong().toString() else String.format(Locale.getDefault(), "%.1f", kg)
