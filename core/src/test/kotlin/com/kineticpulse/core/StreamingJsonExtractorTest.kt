package com.kineticpulse.core

import com.kineticpulse.core.model.ExerciseModality
import com.kineticpulse.core.quicklog.StreamingJsonExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingJsonExtractorTest {

    @Test
    fun `emite objetos en cuanto se cierran aunque lleguen troceados`() {
        val extractor = StreamingJsonExtractor()
        val chunks = listOf(
            "```json\n{\"exercise\":\"Planche ",
            "Lean\",\"modality\":\"isometric\",\"sets\":4,",
            "\"reps\":10,\"holdSeconds\":5}\n{\"exer",
            "cise\":\"dominadas\",\"modality\":\"bodyweight\",\"sets\":3,\"reps\":8}```",
        )
        val emitted = chunks.map { extractor.feed(it) }

        assertTrue(emitted[0].isEmpty())
        assertTrue(emitted[1].isEmpty())
        assertEquals("planche lean", emitted[2].single().exercise)
        assertEquals(ExerciseModality.ISOMETRIC, emitted[2].single().modality)
        assertEquals("dominadas", emitted[3].single().exercise)
    }

    @Test
    fun `llaves dentro de strings no rompen el parseo`() {
        val out = StreamingJsonExtractor()
            .feed("""{"exercise":"remo","modality":"weighted","sets":3,"reps":10,"weightKg":60,"notes":"agarre {prono}"}""")
        assertEquals("agarre {prono}", out.single().notes)
    }

    @Test
    fun `json invalido se descarta y valores absurdos se acotan`() {
        val extractor = StreamingJsonExtractor()
        val bad = extractor.feed("""{"exercise": 12 sin comillas}""")
        val clamped = extractor.feed("""{"exercise":"curl","modality":"weighted","sets":99,"reps":10,"weightKg":20,"rpe":14}""")
        assertTrue(bad.isEmpty())
        assertEquals(20, clamped.single().sets)
        assertEquals(10.0, clamped.single().rpe!!, 0.0)
    }
}
