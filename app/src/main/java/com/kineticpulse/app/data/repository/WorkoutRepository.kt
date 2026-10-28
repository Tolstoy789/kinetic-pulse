package com.kineticpulse.app.data.repository

import androidx.room.withTransaction
import com.kineticpulse.app.data.db.KineticDatabase
import com.kineticpulse.app.di.ApplicationScope
import com.kineticpulse.app.data.db.SetEntryEntity
import com.kineticpulse.app.data.db.SetSource
import com.kineticpulse.app.data.db.SetWithExercise
import com.kineticpulse.app.data.db.WorkoutSessionEntity
import com.kineticpulse.core.fatigue.CompletedSet
import com.kineticpulse.core.fatigue.FatigueReport
import com.kineticpulse.core.fatigue.RpeFatigueAnalyzer
import com.kineticpulse.core.model.RoutineSetDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val db: KineticDatabase,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val workoutDao = db.workoutDao()
    private val exerciseDao = db.exerciseDao()

    private fun today(): Long = LocalDate.now(clock).toEpochDay()

    suspend fun startSession(routineId: Long? = null): Long =
        workoutDao.insertSession(
            WorkoutSessionEntity(routineId = routineId, startedAt = clock.millis(), epochDay = today()),
        )

    suspend fun finishSession(sessionId: Long) = workoutDao.finishSession(sessionId, clock.millis())

    /** Para llamar desde `onCleared`, cuando el scope del ViewModel ya no existe. */
    fun finishSessionAsync(sessionId: Long) {
        appScope.launch { finishSession(sessionId) }
    }

    /** Expande cada DTO ("4x10") en N filas de set, numeradas tras los sets previos del ejercicio. */
    suspend fun logSets(sessionId: Long, items: List<RoutineSetDto>, source: SetSource) {
        if (items.isEmpty()) return
        db.withTransaction {
            val now = clock.millis()
            val rows = items.flatMap { dto ->
                val exerciseId = exerciseDao.getOrCreate(dto.exercise, dto.modality)
                val start = workoutDao.lastSetIndex(sessionId, exerciseId)
                (1..dto.sets).map { i ->
                    SetEntryEntity(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        setIndex = start + i,
                        reps = dto.reps,
                        weightKg = dto.weightKg,
                        holdSeconds = dto.holdSeconds,
                        rpe = dto.rpe,
                        restSeconds = dto.restSeconds,
                        source = source,
                        loggedAt = now,
                    )
                }
            }
            workoutDao.insertSets(rows)
        }
    }

    fun observeSessionSets(sessionId: Long): Flow<List<SetWithExercise>> =
        workoutDao.observeSessionSets(sessionId)

    /** Informe de fatiga reactivo sobre los últimos 28 días. */
    fun observeFatigue(): Flow<FatigueReport> {
        val today = today()
        return workoutDao.observeSetsSince(today - 27).map { rows ->
            val loads = rows.groupBy { it.epochDay }.map { (day, sets) ->
                RpeFatigueAnalyzer.sessionLoad(
                    day,
                    sets.map { CompletedSet(it.reps, it.weightKg, it.holdSeconds, it.rpe) },
                )
            }
            RpeFatigueAnalyzer.analyze(today, loads)
        }
    }
}
