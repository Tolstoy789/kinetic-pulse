package com.kineticpulse.core

import com.kineticpulse.core.model.ExerciseModality
import com.kineticpulse.core.quicklog.HeuristicQuickLogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeuristicQuickLogParserTest {

    @Test
    fun `planche lean con pausa es isometrico`() {
        val result = HeuristicQuickLogParser.parse("4x10 planche lean 5s pausa").single()
        assertEquals("planche lean", result.exercise)
        assertEquals(ExerciseModality.ISOMETRIC, result.modality)
        assertEquals(4, result.sets)
        assertEquals(10, result.reps)
        assertEquals(5, result.holdSeconds)
    }

    @Test
    fun `sentadilla con kilos y rpe es weighted`() {
        val result = HeuristicQuickLogParser.parse("sentadilla 5x5 100kg rpe 8").single()
        assertEquals("sentadilla", result.exercise)
        assertEquals(ExerciseModality.WEIGHTED, result.modality)
        assertEquals(5, result.sets)
        assertEquals(5, result.reps)
        assertEquals(100.0, result.weightKg!!, 0.0)
        assertEquals(8.0, result.rpe!!, 0.0)
    }

    @Test
    fun `series por tiempo 3x20s`() {
        val result = HeuristicQuickLogParser.parse("front lever 3x20s").single()
        assertEquals(ExerciseModality.ISOMETRIC, result.modality)
        assertEquals(3, result.sets)
        assertNull(result.reps)
        assertEquals(20, result.holdSeconds)
    }

    @Test
    fun `varios bloques separados por coma`() {
        val result = HeuristicQuickLogParser.parse("dominadas 3x8, press banca 4x6 80,5kg descanso 2min")
        assertEquals(2, result.size)
        assertEquals(ExerciseModality.BODYWEIGHT, result[0].modality)
        assertEquals("press banca", result[1].exercise)
        assertEquals(80.5, result[1].weightKg!!, 0.0)
        assertEquals(120, result[1].restSeconds)
    }
}
