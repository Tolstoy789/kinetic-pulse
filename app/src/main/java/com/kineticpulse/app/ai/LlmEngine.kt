package com.kineticpulse.app.ai

import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Abstracción mínima del runtime LLM. Aísla la API de LiteRT-LM (que aún evoluciona entre
 * versiones) en una sola clase y permite tests sin dispositivo.
 */
interface LlmEngine : AutoCloseable {
    /** Bloqueante y costoso (segundos): llamar siempre fuera del hilo principal. */
    fun initialize()

    /** Genera una respuesta sin historial y emite los fragmentos de texto según llegan. */
    fun generate(systemInstruction: String, prompt: String, temperature: Float): Flow<String>
}

fun interface LlmEngineFactory {
    fun create(model: File, backend: ComputeBackend, cacheDir: File): LlmEngine
}
