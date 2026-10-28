package com.kineticpulse.core.quicklog

/**
 * Prompt few-shot para el "NLP Quick Log". Se mantiene corto a propósito: cada token de
 * prefill cuenta para la latencia del primer token en el dispositivo.
 */
object QuickLogPrompt {

    const val SYSTEM_INSTRUCTION: String =
        "Eres un parser de entrenamiento. Convierte notas en JSON. " +
            "Responde SOLO con objetos JSON, uno por línea, sin texto adicional."

    private val SCHEMA = """
        Campos: exercise (string), modality ("weighted"|"bodyweight"|"isometric"), sets (int),
        reps (int|null), weightKg (number|null), holdSeconds (int|null), restSeconds (int|null),
        rpe (number|null), notes (string|null).
    """.trimIndent()

    private val EXAMPLES = """
        Nota: 4x10 planche lean 5s pausa
        {"exercise":"planche lean","modality":"isometric","sets":4,"reps":10,"holdSeconds":5}
        Nota: sentadilla 5x5 100kg rpe 8, dominadas 3x8
        {"exercise":"sentadilla","modality":"weighted","sets":5,"reps":5,"weightKg":100,"rpe":8}
        {"exercise":"dominadas","modality":"bodyweight","sets":3,"reps":8}
    """.trimIndent()

    fun build(note: String): String = buildString {
        appendLine(SCHEMA)
        appendLine(EXAMPLES)
        append("Nota: ").appendLine(note.trim().replace('\n', ' '))
    }
}
