package com.kineticpulse.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kineticpulse.core.model.ExerciseModality
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(exercise: ExerciseEntity): Long

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    abstract suspend fun findByName(name: String): ExerciseEntity?

    @Query("SELECT * FROM exercises ORDER BY name")
    abstract fun observeAll(): Flow<List<ExerciseEntity>>

    /** Ejercicios ordenados por uso reciente: los que acabas de hacer salen arriba. */
    @Query(
        """
        SELECT e.* FROM exercises e LEFT JOIN set_entries s ON s.exercise_id = e.id
        GROUP BY e.id ORDER BY MAX(s.logged_at) DESC, e.name
        """,
    )
    abstract fun observeByRecentUse(): Flow<List<ExerciseEntity>>

    /** Devuelve el id existente o crea el ejercicio. Llamar dentro de una transacción. */
    open suspend fun getOrCreate(name: String, modality: ExerciseModality): Long =
        findByName(name)?.id ?: insert(ExerciseEntity(name = name, modality = modality))
}

@Dao
interface RoutineDao {
    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(items: List<RoutineExerciseEntity>)

    @Query("SELECT * FROM routines ORDER BY created_at DESC")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_exercises WHERE routine_id = :routineId ORDER BY position")
    suspend fun exercisesFor(routineId: Long): List<RoutineExerciseEntity>
}

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Query("SELECT * FROM workout_sessions WHERE ended_at IS NULL ORDER BY started_at DESC")
    suspend fun openSessions(): List<WorkoutSessionEntity>

    @Query("UPDATE workout_sessions SET ended_at = :endedAt WHERE id = :sessionId")
    suspend fun finishSession(sessionId: Long, endedAt: Long)

    @Insert
    suspend fun insertSets(sets: List<SetEntryEntity>)

    @Query("DELETE FROM set_entries WHERE id = (SELECT MAX(id) FROM set_entries WHERE session_id = :sessionId)")
    suspend fun deleteLastSet(sessionId: Long)

    /** Último set de un ejercicio (cualquier sesión): sirve para proponer peso y reps. */
    @Query(
        """
        SELECT s.* FROM set_entries s JOIN exercises e ON e.id = s.exercise_id
        WHERE e.name = :exerciseName ORDER BY s.logged_at DESC, s.id DESC LIMIT 1
        """,
    )
    suspend fun lastSetFor(exerciseName: String): SetEntryEntity?

    /** Entrenos con al menos un set desde [fromEpochDay]. */
    @Query(
        """
        SELECT COUNT(DISTINCT w.id) FROM workout_sessions w JOIN set_entries s ON s.session_id = w.id
        WHERE w.epoch_day >= :fromEpochDay
        """,
    )
    fun observeSessionCountSince(fromEpochDay: Long): Flow<Int>

    @Query("SELECT COALESCE(MAX(set_index), 0) FROM set_entries WHERE session_id = :sessionId AND exercise_id = :exerciseId")
    suspend fun lastSetIndex(sessionId: Long, exerciseId: Long): Int

    @Query(
        """
        SELECT s.id, s.set_index, s.reps, s.weight_kg, s.hold_seconds, s.rpe, s.source,
               e.name AS exercise_name, e.modality
        FROM set_entries s JOIN exercises e ON e.id = s.exercise_id
        WHERE s.session_id = :sessionId
        ORDER BY s.logged_at DESC, s.id DESC
        """,
    )
    fun observeSessionSets(sessionId: Long): Flow<List<SetWithExercise>>

    @Query(
        """
        SELECT w.epoch_day, s.reps, s.weight_kg, s.hold_seconds, s.rpe
        FROM set_entries s JOIN workout_sessions w ON w.id = s.session_id
        WHERE w.epoch_day >= :fromEpochDay
        """,
    )
    fun observeSetsSince(fromEpochDay: Long): Flow<List<SessionSetRow>>
}
