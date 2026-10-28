package com.kineticpulse.app.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Implementación sobre LiteRT-LM. `Backend.GPU()` usa el delegado OpenCL de la
 * Adreno 830; requiere los `<uses-native-library>` declarados en el manifest.
 */
class LiteRtLmEngine(
    model: File,
    backend: ComputeBackend,
    cacheDir: File,
) : LlmEngine {

    private val engine = Engine(
        EngineConfig(
            modelPath = model.absolutePath,
            backend = when (backend) {
                ComputeBackend.GPU -> Backend.GPU()
                ComputeBackend.CPU -> Backend.CPU()
            },
            // Cachea los kernels OpenCL compilados: la segunda carga es mucho más rápida.
            cacheDir = cacheDir.absolutePath,
        ),
    )

    override fun initialize() = engine.initialize()

    override fun generate(systemInstruction: String, prompt: String, temperature: Float): Flow<String> = flow {
        // Conversación nueva por petición: el Quick Log no debe arrastrar historial.
        val conversation = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = temperature.toDouble()),
            ),
        )
        try {
            conversation.sendMessageAsync(prompt).collect { message -> emit(message.toString()) }
        } finally {
            conversation.close()
        }
    }

    override fun close() = engine.close()

    companion object {
        val Factory = LlmEngineFactory { model, backend, cacheDir -> LiteRtLmEngine(model, backend, cacheDir) }
    }
}
