package com.kineticpulse.core.fatigue

import kotlin.math.roundToInt

/** Un set ya completado, con lo mínimo para estimar carga interna. */
data class CompletedSet(
    val reps: Int?,
    val weightKg: Double?,
    val holdSeconds: Int?,
    val rpe: Double?,
)

/** Resumen de una sesión para el análisis de fatiga entre sesiones. */
data class SessionLoad(
    val epochDay: Long,
    /** Carga interna = Σ (esfuerzo del set × RPE). Adimensional, comparable entre sesiones. */
    val internalLoad: Double,
    val averageRpe: Double,
)

enum class FatigueLevel { FRESH, PRODUCTIVE, HIGH, OVERREACHING }

data class FatigueReport(
    val level: FatigueLevel,
    /** Ratio carga aguda (7 días) / crónica (28 días). 0.8–1.3 es la "zona productiva". */
    val acuteChronicRatio: Double,
    val averageRpeLast7Days: Double,
    val recommendation: String,
)

/**
 * Análisis de fatiga basado en RPE (sRPE / ratio agudo:crónico). Determinista y barato:
 * corre en cada set sin necesidad del LLM. El LLM grande solo se usa para redactar
 * microciclos a partir de este informe.
 */
object RpeFatigueAnalyzer {

    private const val DEFAULT_RPE = 7.0

    /** Esfuerzo de un set: reps dinámicas o segundos isométricos (1 rep ≈ 3 s bajo tensión). */
    fun setEffort(set: CompletedSet): Double {
        val dynamic = (set.reps ?: 0).toDouble()
        val isometric = (set.holdSeconds ?: 0) / 3.0
        return (dynamic + isometric).coerceAtLeast(1.0)
    }

    fun sessionLoad(epochDay: Long, sets: List<CompletedSet>): SessionLoad {
        if (sets.isEmpty()) return SessionLoad(epochDay, 0.0, 0.0)
        val rpes = sets.map { it.rpe ?: DEFAULT_RPE }
        val load = sets.zip(rpes).sumOf { (set, rpe) -> setEffort(set) * rpe }
        return SessionLoad(epochDay, load, rpes.average())
    }

    /** Estimación de 1RM (Epley) ajustada por repeticiones en reserva derivadas del RPE. */
    fun estimatedOneRepMax(weightKg: Double, reps: Int, rpe: Double?): Double {
        val rir = (10.0 - (rpe ?: 10.0)).coerceIn(0.0, 5.0)
        val effectiveReps = reps + rir
        return if (effectiveReps <= 1.0) weightKg else weightKg * (1 + effectiveReps / 30.0)
    }

    fun analyze(today: Long, history: List<SessionLoad>): FatigueReport {
        val acuteWindow = history.filter { today - it.epochDay in 0..6 }
        val chronicWindow = history.filter { today - it.epochDay in 0..27 }

        val acute = acuteWindow.sumOf { it.internalLoad }
        val chronicWeekly = chronicWindow.sumOf { it.internalLoad } / 4.0
        val ratio = if (chronicWeekly > 0.0) acute / chronicWeekly else 0.0
        val avgRpe = acuteWindow.filter { it.averageRpe > 0 }.map { it.averageRpe }.average().takeIf { !it.isNaN() } ?: 0.0

        val level = when {
            ratio > 1.5 || avgRpe >= 9.3 -> FatigueLevel.OVERREACHING
            ratio > 1.3 || avgRpe >= 8.7 -> FatigueLevel.HIGH
            ratio >= 0.8 -> FatigueLevel.PRODUCTIVE
            else -> FatigueLevel.FRESH
        }

        val recommendation = when (level) {
            FatigueLevel.OVERREACHING -> "Descarga: reduce el volumen ~40% y mantén RPE ≤ 7 esta semana."
            FatigueLevel.HIGH -> "Mantén cargas, recorta 1–2 series por ejercicio y vigila el RPE."
            FatigueLevel.PRODUCTIVE -> "Zona productiva: progresa +2.5 kg o +1 rep / +2 s en isometrías."
            FatigueLevel.FRESH -> "Margen disponible: puedes subir volumen o intensidad."
        }

        return FatigueReport(level, (ratio * 100).roundToInt() / 100.0, (avgRpe * 10).roundToInt() / 10.0, recommendation)
    }
}
