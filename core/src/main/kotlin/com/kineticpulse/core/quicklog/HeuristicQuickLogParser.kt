package com.kineticpulse.core.quicklog

import com.kineticpulse.core.model.ExerciseModality
import com.kineticpulse.core.model.RoutineSetDto

/**
 * Parser determinista por expresiones regulares. Se usa cuando el modelo aún no está
 * cargado o no se ha copiado al dispositivo, y como red de seguridad si el LLM no devuelve
 * JSON válido. Cubre los formatos más habituales: "4x10", "5x5 100kg", "3x20s", "rpe 8".
 */
object HeuristicQuickLogParser {

    /** Coma separa bloques salvo entre dígitos ("80,5kg" es un decimal). */
    private val BLOCK_SEPARATOR = Regex("""(?<!\d),|,(?!\d)|[;\n+]""")
    private val SETS_REPS = Regex("""(\d+)\s*[x×*]\s*(\d+)\s*(s|seg|sec)?\b""", RegexOption.IGNORE_CASE)
    private val WEIGHT = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|kgs|kilos?)\b""", RegexOption.IGNORE_CASE)
    private val HOLD = Regex("""(\d+)\s*(s|seg|segs|segundos|sec)\b""", RegexOption.IGNORE_CASE)
    private val REST = Regex("""(?:descanso|rest)\s*(\d+)\s*(s|seg|min|m)?\b""", RegexOption.IGNORE_CASE)
    private val RPE = Regex("""rpe\s*(\d+(?:[.,]\d)?)""", RegexOption.IGNORE_CASE)
    private val PAUSE_WORDS = Regex("""\b(pausa|hold|isom[eé]trico|isometria|isometría)\b""", RegexOption.IGNORE_CASE)

    private val ISOMETRIC_KEYWORDS = listOf(
        "planche", "lever", "l-sit", "lsit", "plancha", "hollow", "handstand", "parada de manos",
        "dead hang", "wall sit", "v-sit", "hold",
    )

    fun parse(note: String): List<RoutineSetDto> =
        note.split(BLOCK_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull(::parseBlock)

    private fun parseBlock(block: String): RoutineSetDto? {
        val setsReps = SETS_REPS.find(block)
        val weight = WEIGHT.find(block)
        val rpe = RPE.find(block)
        val rest = REST.find(block)

        val setsRepsIsTime = setsReps?.groupValues?.get(3)?.isNotEmpty() == true
        val hold = if (setsRepsIsTime) null else HOLD.findAll(block)
            .firstOrNull { rest == null || it.range.first !in rest.range }

        val name = block
            .let { s -> listOfNotNull(setsReps, weight, rpe, rest, hold).fold(s) { acc, m -> acc.replace(m.value, " ") } }
            .replace(PAUSE_WORDS, " ")
            .replace(Regex("""[^\p{L}\s-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()
            .ifEmpty { return null }

        val isIsometric = setsRepsIsTime || hold != null && PAUSE_WORDS.containsMatchIn(block) ||
            ISOMETRIC_KEYWORDS.any { name.contains(it) } && (hold != null || setsRepsIsTime)

        val sets = setsReps?.groupValues?.get(1)?.toInt() ?: 1
        val second = setsReps?.groupValues?.get(2)?.toInt()

        val modality = when {
            isIsometric -> ExerciseModality.ISOMETRIC
            weight != null -> ExerciseModality.WEIGHTED
            else -> ExerciseModality.BODYWEIGHT
        }

        return RoutineSetDto(
            exercise = name,
            modality = modality,
            sets = sets,
            reps = if (setsRepsIsTime) null else second,
            weightKg = weight?.groupValues?.get(1)?.replace(',', '.')?.toDouble(),
            holdSeconds = if (setsRepsIsTime) second else hold?.groupValues?.get(1)?.toInt(),
            restSeconds = rest?.let {
                val value = it.groupValues[1].toInt()
                if (it.groupValues[2].startsWith("m", ignoreCase = true)) value * 60 else value
            },
            rpe = rpe?.groupValues?.get(1)?.replace(',', '.')?.toDouble(),
        ).sanitized()
    }
}
