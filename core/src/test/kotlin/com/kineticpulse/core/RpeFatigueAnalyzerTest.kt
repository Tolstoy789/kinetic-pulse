package com.kineticpulse.core

import com.kineticpulse.core.fatigue.CompletedSet
import com.kineticpulse.core.fatigue.FatigueLevel
import com.kineticpulse.core.fatigue.RpeFatigueAnalyzer
import com.kineticpulse.core.fatigue.SessionLoad
import org.junit.Assert.assertEquals
import org.junit.Test

class RpeFatigueAnalyzerTest {

    @Test
    fun `carga de sesion combina reps e isometria`() {
        val load = RpeFatigueAnalyzer.sessionLoad(
            epochDay = 0,
            sets = listOf(
                CompletedSet(reps = 10, weightKg = 50.0, holdSeconds = null, rpe = 8.0),
                CompletedSet(reps = null, weightKg = null, holdSeconds = 30, rpe = 9.0),
            ),
        )
        assertEquals(10 * 8.0 + 10 * 9.0, load.internalLoad, 1e-9)
        assertEquals(8.5, load.averageRpe, 1e-9)
    }

    @Test
    fun `1RM usa RIR derivado del RPE`() {
        assertEquals(100.0 * (1 + 7 / 30.0), RpeFatigueAnalyzer.estimatedOneRepMax(100.0, 5, 8.0), 1e-9)
    }

    @Test
    fun `semana estable es productiva y pico agudo es overreaching`() {
        val steady = (0L until 28L step 2).map { SessionLoad(it, 100.0, 7.5) }
        assertEquals(FatigueLevel.PRODUCTIVE, RpeFatigueAnalyzer.analyze(27, steady).level)

        val spike = steady.filter { it.epochDay < 21 } + (21L..27L).map { SessionLoad(it, 250.0, 9.0) }
        assertEquals(FatigueLevel.OVERREACHING, RpeFatigueAnalyzer.analyze(27, spike).level)
    }
}
