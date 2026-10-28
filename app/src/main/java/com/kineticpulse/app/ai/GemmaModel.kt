package com.kineticpulse.app.ai

/**
 * Modelos soportados. Los ficheros se copian con `adb push` a
 * `/sdcard/Android/data/com.kineticpulse.app/files/models/` (ver README).
 *
 * @property minFreeRamMb RAM libre mínima para cargar el modelo sin que el sistema
 *   mate la app (pesos + KV cache + buffers OpenCL, medido aprox. en GPU).
 */
enum class GemmaModel(
    val fileName: String,
    val displayName: String,
    val minFreeRamMb: Long,
    val maxTokens: Int,
) {
    /** Respuesta instantánea: Quick Log y clasificaciones cortas. */
    FAST(
        fileName = "gemma3-1b-it-int4.litertlm",
        displayName = "Gemma 3 1B",
        minFreeRamMb = 1_500,
        maxTokens = 1_024,
    ),

    /** Generación compleja: microciclos y análisis de fatiga narrado. */
    COMPLEX(
        fileName = "gemma-3n-E4B-it-int4.litertlm",
        displayName = "Gemma 3n E4B",
        minFreeRamMb = 5_000,
        maxTokens = 4_096,
    ),
}

enum class ComputeBackend { GPU, CPU }
