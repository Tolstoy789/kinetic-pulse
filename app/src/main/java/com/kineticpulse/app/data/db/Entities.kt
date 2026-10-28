package com.kineticpulse.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kineticpulse.core.model.ExerciseModality

/** Catálogo de ejercicios. La modalidad decide qué columnas de [SetEntryEntity] se usan. */
@Entity(tableName = "exercises", indices = [Index(value = ["name"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val modality: ExerciseModality,
)

/** Plantilla de rutina (p. ej. "Empuje A", "Skills calistenia"). */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Ejercicio dentro de una rutina con sus objetivos de sobrecarga progresiva. */
@Entity(
    tableName = "routine_exercises",
    primaryKeys = ["routine_id", "position"],
    foreignKeys = [
        ForeignKey(RoutineEntity::class, ["id"], ["routine_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exercise_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("exercise_id")],
)
data class RoutineExerciseEntity(
    @ColumnInfo(name = "routine_id") val routineId: Long,
    val position: Int,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    @ColumnInfo(name = "target_sets") val targetSets: Int,
    @ColumnInfo(name = "target_reps") val targetReps: Int? = null,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Double? = null,
    @ColumnInfo(name = "target_hold_seconds") val targetHoldSeconds: Int? = null,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int? = null,
)

/** Una sesión real de entrenamiento, opcionalmente basada en una rutina. */
@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(RoutineEntity::class, ["id"], ["routine_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("routine_id"), Index("epoch_day")],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "routine_id") val routineId: Long? = null,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    val notes: String? = null,
)

enum class SetSource { MANUAL, QUICK_LOG_AI, QUICK_LOG_HEURISTIC }

/**
 * Un set ejecutado. Gimnasio usa weight_kg + reps; calistenia dinámica usa reps
 * (weight_kg = lastre); isometría usa hold_seconds (+ reps si son repeticiones con pausa).
 */
@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(WorkoutSessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exercise_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("session_id"), Index("exercise_id")],
)
data class SetEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    @ColumnInfo(name = "set_index") val setIndex: Int,
    val reps: Int? = null,
    @ColumnInfo(name = "weight_kg") val weightKg: Double? = null,
    @ColumnInfo(name = "hold_seconds") val holdSeconds: Int? = null,
    val rpe: Double? = null,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int? = null,
    val source: SetSource = SetSource.MANUAL,
    @ColumnInfo(name = "logged_at") val loggedAt: Long,
)

/** Proyección para la UI: set + nombre/modalidad del ejercicio. */
data class SetWithExercise(
    val id: Long,
    @ColumnInfo(name = "set_index") val setIndex: Int,
    val reps: Int?,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    @ColumnInfo(name = "hold_seconds") val holdSeconds: Int?,
    val rpe: Double?,
    val source: SetSource,
    @ColumnInfo(name = "exercise_name") val exerciseName: String,
    val modality: ExerciseModality,
)

/** Agregado por sesión para el análisis de fatiga. */
data class SessionSetRow(
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    val reps: Int?,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    @ColumnInfo(name = "hold_seconds") val holdSeconds: Int?,
    val rpe: Double?,
)
