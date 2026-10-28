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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
 *  - Nunca cierra el motor con una generación en curso: [unload] espera a que terminen
 *    (cerrar el motor nativo a mitad de stream es un crash, no una excepción).
 *  - En segundo plano libera el modelo tras [GemmaModel.backgroundGrace] y lo recarga al volver.
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
    @Volatile private var engine: LlmEngine? = null
    private var loadJob: Job? = null
    private var backgroundUnloadJob: Job? = null

    /** Generaciones que tienen prestado [engine]. Solo se cierra el motor cuando vale 0. */
    private val activeGenerations = MutableStateFlow(0)

    /** Último modelo pedido; se recarga al volver a primer plano si se liberó en segundo plano. */
    @Volatile private var wantedModel: GemmaModel? = null

    private val _state = MutableStateFlow<EngineState>(EngineState.Unloaded)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Lanza la carga en segundo plano y vuelve inmediatamente. Idempotente. */
    fun loadAsync(model: GemmaModel = GemmaModel.FAST): Job {
        wantedModel = model
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
        val sizeMb = file.length() / (1024 * 1024)
        if (file.length() < model.minFileSizeBytes) {
            return EngineState.Error(
                model,
                "Modelo incompleto ($sizeMb MB, se esperaban ≥ ${model.minFileSizeBytes / (1024 * 1024)} MB). " +
                    "Vuelve a copiarlo con scripts/push-models.sh",
            ).also { _state.value = it }
        }

        // Liberar el modelo anterior ANTES de medir RAM: nunca dos modelos residentes.
        if (!releaseLocked()) {
            return EngineState.Error(model, "Generación en curso; reintenta en unos segundos")
        }

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

    /** Presión de memoria real (`onLowMemory`): libera pesos y buffers GPU ya. */
    fun onMemoryPressure() {
        backgroundUnloadJob?.cancel()
        scope.launch(ioDispatcher) { unload() }
    }

    /**
     * La app deja de verse. El modelo pequeño se conserva unos minutos (volver de la música
     * entre series no debe costar una recarga); el grande se libera ya.
     */
    fun onAppBackgrounded() {
        val model = (_state.value as? EngineState.Ready)?.model ?: return
        // UI_HIDDEN y BACKGROUND llegan seguidos: no reiniciar la cuenta atrás.
        if (backgroundUnloadJob?.isActive == true) return
        backgroundUnloadJob = scope.launch(ioDispatcher) {
            delay(model.backgroundGrace)
            unload()
        }
    }

    /** La app vuelve a primer plano: cancela la descarga pendiente o recarga lo que se liberó. */
    fun onAppForegrounded() {
        backgroundUnloadJob?.cancel()
        backgroundUnloadJob = null
        val model = wantedModel ?: return
        if (_state.value is EngineState.Unloaded) loadAsync(model)
    }

    suspend fun unload() = mutex.withLock {
        if (releaseLocked()) _state.value = EngineState.Unloaded
    }

    /**
     * Cierra el motor cuando no queda ninguna generación usándolo. Devuelve false (y no cierra)
     * si tras [DRAIN_TIMEOUT] sigue habiendo una: mejor conservar memoria que un crash nativo.
     * Las generaciones nuevas no pueden empezar mientras se tiene el [mutex].
     */
    private suspend fun releaseLocked(): Boolean {
        val current = engine ?: return true
        val drained = withTimeoutOrNull(DRAIN_TIMEOUT) { activeGenerations.first { it == 0 } } != null
        if (!drained) {
            Log.w(TAG, "Generación en curso tras $DRAIN_TIMEOUT; no se libera el modelo")
            return false
        }
        runCatching { current.close() }
        engine = null
        return true
    }

    /** Presta el motor a una generación; null si no hay modelo listo. Liberar con [returnEngine]. */
    private suspend fun borrowEngine(): LlmEngine? = mutex.withLock {
        engine?.takeIf { _state.value is EngineState.Ready }?.also { activeGenerations.update { n -> n + 1 } }
    }

    private fun returnEngine() = activeGenerations.update { it - 1 }

    /**
     * NLP Quick Log: "4x10 planche lean 5s pausa" → [RoutineSetDto] en streaming.
     * Sin modelo listo (o si el LLM no produce JSON válido) usa el parser heurístico,
     * así el registro nunca se bloquea.
     */
    fun parseQuickLog(input: String): Flow<RoutineSetDto> = flow {
        val llm = borrowEngine()
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
        } finally {
            returnEngine()
        }
        if (emitted == 0) HeuristicQuickLogParser.parse(input).forEach { emit(it) }
    }.flowOn(ioDispatcher)

    /** Texto libre en streaming (p. ej. microciclos con [GemmaModel.COMPLEX]). */
    fun generateText(systemInstruction: String, prompt: String): Flow<String> = flow {
        val llm = borrowEngine() ?: error("Modelo no cargado")
        try {
            llm.generate(systemInstruction, prompt, temperature = 0.7f).collect { emit(it) }
        } finally {
            returnEngine()
        }
    }.flowOn(ioDispatcher)

    private companion object {
        const val TAG = "GemmaLocalManager"
        val DRAIN_TIMEOUT = 30.seconds
    }
}
