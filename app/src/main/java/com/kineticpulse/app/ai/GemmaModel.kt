package com.kineticpulse.app.ai

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes

/**
 * Modelos soportados. Los ficheros se copian con `adb push` a
 * `/sdcard/Android/data/com.kineticpulse.app/files/models/` (ver README).
 *
 * @property minFreeRamMb RAM libre mínima para cargar el modelo sin que el sistema
 *   mate la app (pesos + KV cache + buffers OpenCL, medido aprox. en GPU).
 * @property minFileSizeBytes cota inferior holgada del `.litertlm`: por debajo, el fichero
 *   está truncado (un `adb push` interrumpido) y cargarlo puede tumbar el proceso en nativo.
 * @property backgroundGrace cuánto se mantiene cargado con la app en segundo plano. Salir a la
 *   música entre series no debe obligar a recargar; el modelo grande sale enseguida.
 */
enum class GemmaModel(
    val fileName: String,
    val displayName: String,
    val minFreeRamMb: Long,
    val maxTokens: Int,
    val minFileSizeBytes: Long,
    val backgroundGrace: Duration,
) {
    /** Respuesta instantánea: Quick Log y clasificaciones cortas. */
    FAST(
        fileName = "gemma3-1b-it-int4.litertlm",
        displayName = "Gemma 3 1B",
        minFreeRamMb = 1_500,
        maxTokens = 1_024,
        minFileSizeBytes = 300L * MB,
        backgroundGrace = 5.minutes,
    ),

    /** Generación compleja: microciclos y análisis de fatiga narrado. */
    COMPLEX(
        fileName = "gemma-3n-E4B-it-int4.litertlm",
        displayName = "Gemma 3n E4B",
        minFreeRamMb = 5_000,
        maxTokens = 4_096,
        minFileSizeBytes = 2_000L * MB,
        backgroundGrace = ZERO,
    ),
}

private const val MB = 1024L * 1024L

enum class ComputeBackend { GPU, CPU }
