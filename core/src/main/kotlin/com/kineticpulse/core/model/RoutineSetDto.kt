package com.kineticpulse.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Modalidad de un ejercicio: define qué métricas son obligatorias. */
@Serializable
enum class ExerciseModality {
    /** Gimnasio con carga externa: kilos × reps (sobrecarga progresiva). */
    @SerialName("weighted") WEIGHTED,

    /** Calistenia dinámica: reps con peso corporal (lastre opcional). */
    @SerialName("bodyweight") BODYWEIGHT,

    /** Calistenia isométrica: tiempo bajo tensión (planche, front lever, L-sit…). */
    @SerialName("isometric") ISOMETRIC,
}

/**
 * Contrato JSON que el modelo on-device debe devolver para cada bloque del Quick Log.
 *
 * Ejemplo: "4x10 planche lean 5s pausa" →
 * `{"exercise":"planche lean","modality":"isometric","sets":4,"reps":10,"holdSeconds":5}`
 */
@Serializable
data class RoutineSetDto(
    val exercise: String,
    val modality: ExerciseModality,
    val sets: Int = 1,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val holdSeconds: Int? = null,
    val restSeconds: Int? = null,
    val rpe: Double? = null,
    val notes: String? = null,
) {
    /** Normaliza valores fuera de rango que un LLM pequeño puede alucinar. */
    fun sanitized(): RoutineSetDto = copy(
        exercise = exercise.trim().lowercase(),
        sets = sets.coerceIn(1, 20),
        reps = reps?.coerceIn(1, 200),
        weightKg = weightKg?.coerceIn(0.0, 500.0),
        holdSeconds = holdSeconds?.coerceIn(1, 600),
        restSeconds = restSeconds?.coerceIn(0, 900),
        rpe = rpe?.coerceIn(1.0, 10.0),
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
    )

    /** Volumen de tonelaje (kg·reps). Cero en isometrías y en peso corporal sin lastre. */
    val tonnageKg: Double
        get() = sets * (reps ?: 0) * (weightKg ?: 0.0)

    /** Tiempo total bajo tensión en segundos para trabajo isométrico. */
    val timeUnderTensionSeconds: Int
        get() = if (modality == ExerciseModality.ISOMETRIC) sets * (reps ?: 1) * (holdSeconds ?: 0) else 0
}
