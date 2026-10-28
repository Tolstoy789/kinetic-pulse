package com.kineticpulse.app.ai

import android.util.Log
import com.kineticpulse.core.model.RoutineSetDto
import com.kineticpulse.core.quicklog.HeuristicQuickLogParser
import com.kineticpulse.core.quicklog.QuickLogPrompt
import com.kineticpulse.core.quicklog.StreamingJsonExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration
import kotlin.time.TimeSource

sealed interface EngineState {
    data object Unloaded : EngineState
    data class Loading(val model: GemmaModel) : EngineState
    data class Ready(val model: GemmaModel, val backend: ComputeBackend, val loadTime: Duration) : EngineState
    data class ModelMissing(val model: GemmaModel, val expectedPath: String) : EngineState
    data class Error(val model: GemmaModel, val message: String) : EngineState
}

/**
 * Ciclo de vida del LLM on-device:
 *  - Carga asíncrona en [ioDispatcher] (nunca en Main), un solo modelo residente a la vez.
 *  - Comprueba RAM libre antes de cargar y libera el modelo ante presión de memoria.
 *  - GPU obligatoria por defecto; si el delegado falla, reintenta en CPU y lo informa.
 *  - [parseQuickLog] emite cada set en cuanto su JSON se completa en el stream.
 */
class GemmaLocalManager(
    private val resources: DeviceResources,
    private val engineFactory: LlmEngineFactory,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var engine: LlmEngine? = null
    private var loadJob: Job? = null

    private val _state = MutableStateFlow<EngineState>(EngineState.Unloaded)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Lanza la carga en segundo plano y vuelve inmediatamente. Idempotente. */
    fun loadAsync(model: GemmaModel = GemmaModel.FAST): Job {
        val current = _state.value
        if (current is EngineState.Ready && current.model == model) return Job().apply { complete() }
        loadJob?.takeIf { it.isActive && current is EngineState.Loading && current.model == model }?.let { return it }
        return scope.launch(ioDispatcher) { load(model) }.also { loadJob = it }
    }

    suspend fun load(model: GemmaModel): EngineState = mutex.withLock {
        (_state.value as? EngineState.Ready)?.takeIf { it.model == model }?.let { return it }

        val file = resources.modelsDir.resolve(model.fileName)
        if (!file.isFile) {
            return EngineState.ModelMissing(model, file.absolutePath).also { _state.value = it }
        }

        // Liberar el modelo anterior ANTES de medir RAM: nunca dos modelos residentes.
        releaseLocked()

        val freeMb = resources.availableRamMb()
        if (freeMb < model.minFreeRamMb) {
            return EngineState.Error(model, "RAM libre insuficiente: $freeMb MB < ${model.minFreeRamMb} MB")
                .also { _state.value = it }
        }

        _state.value = EngineState.Loading(model)
        val mark = TimeSource.Monotonic.markNow()
        val result = withContext(ioDispatcher) {
            tryInit(file, ComputeBackend.GPU) ?: tryInit(file, ComputeBackend.CPU)
        }
        val newState = if (result != null) {
            engine = result.first
            EngineState.Ready(model, result.second, mark.elapsedNow())
        } else {
            EngineState.Error(model, "No se pudo inicializar el motor (GPU ni CPU)")
        }
        _state.value = newState
        newState
    }

    private fun tryInit(file: File, backend: ComputeBackend): Pair<LlmEngine, ComputeBackend>? {
        var candidate: LlmEngine? = null
        return try {
            candidate = engineFactory.create(file, backend, resources.cacheDir)
            candidate.initialize()
            candidate to backend
        } catch (t: Throwable) {
            Log.w(TAG, "Fallo al inicializar backend $backend", t)
            runCatching { candidate?.close() }
            null
        }
    }

    /** Llamar desde `onTrimMemory`: libera pesos y buffers GPU si el sistema lo pide. */
    fun onMemoryPressure() {
        scope.launch(ioDispatcher) { unload() }
    }

    suspend fun unload() = mutex.withLock {
        releaseLocked()
        _state.value = EngineState.Unloaded
    }

    private fun releaseLocked() {
        engine?.let { runCatching { it.close() } }
        engine = null
    }

    /**
     * NLP Quick Log: "4x10 planche lean 5s pausa" → [RoutineSetDto] en streaming.
     * Sin modelo listo (o si el LLM no produce JSON válido) usa el parser heurístico,
     * así el registro nunca se bloquea.
     */
    fun parseQuickLog(input: String): Flow<RoutineSetDto> = flow {
        val llm = engine.takeIf { _state.value is EngineState.Ready }
        if (llm == null) {
            HeuristicQuickLogParser.parse(input).forEach { emit(it) }
            return@flow
        }

        val extractor = StreamingJsonExtractor()
        var emitted = 0
        try {
            llm.generate(QuickLogPrompt.SYSTEM_INSTRUCTION, QuickLogPrompt.build(input), temperature = 0.1f)
                .collect { chunk ->
                    extractor.feed(chunk).forEach {
                        emitted++
                        emit(it)
                    }
                }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.w(TAG, "Generación fallida, usando parser heurístico", t)
        }
        if (emitted == 0) HeuristicQuickLogParser.parse(input).forEach { emit(it) }
    }.flowOn(ioDispatcher)

    /** Texto libre en streaming (p. ej. microciclos con [GemmaModel.COMPLEX]). */
    fun generateText(systemInstruction: String, prompt: String): Flow<String> = flow {
        val llm = engine ?: error("Modelo no cargado")
        llm.generate(systemInstruction, prompt, temperature = 0.7f).collect { emit(it) }
    }.flowOn(ioDispatcher)

    private companion object {
        const val TAG = "GemmaLocalManager"
    }
}
