package com.kineticpulse.app.ai

import com.kineticpulse.core.model.ExerciseModality
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class GemmaLocalManagerTest {

    @get:Rule val tmp = TemporaryFolder()

    private class FakeResources(override val modelsDir: File, override val cacheDir: File, var ramMb: Long = 8_000) :
        DeviceResources {
        override fun availableRamMb() = ramMb
        override fun isLowRamDevice() = false
    }

    private class FakeEngine(
        private val chunks: List<String>,
        private val failOn: ComputeBackend? = null,
        private val backend: ComputeBackend,
        private val chunkDelayMs: Long = 0,
    ) : LlmEngine {
        var closed = false
        override fun initialize() {
            if (backend == failOn) throw IllegalStateException("OpenCL no disponible")
        }
        override fun generate(systemInstruction: String, prompt: String, temperature: Float): Flow<String> =
            flow {
                chunks.forEach {
                    if (chunkDelayMs > 0) delay(chunkDelayMs)
                    emit(it)
                }
            }
        override fun close() { closed = true }
    }

    private fun manager(
        scope: TestScope,
        resources: DeviceResources,
        chunks: List<String> = emptyList(),
        failOn: ComputeBackend? = null,
        created: MutableList<FakeEngine> = mutableListOf(),
        chunkDelayMs: Long = 0,
    ): GemmaLocalManager {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return GemmaLocalManager(
            resources = resources,
            engineFactory = { _, backend, _ -> FakeEngine(chunks, failOn, backend, chunkDelayMs).also(created::add) },
            scope = scope,
            ioDispatcher = dispatcher,
        )
    }

    private fun resourcesWith(vararg models: GemmaModel, ram: Long = 8_000): FakeResources {
        val dir = tmp.newFolder("models")
        // Fichero disperso del tamaño mínimo: pasa la verificación sin ocupar disco.
        models.forEach { RandomAccessFile(File(dir, it.fileName), "rw").use { f -> f.setLength(it.minFileSizeBytes) } }
        return FakeResources(dir, tmp.newFolder("cache"), ram)
    }

    @Test
    fun `sin modelo usa el parser heuristico`() = runTest {
        val m = manager(this, resourcesWith())
        assertTrue(m.load(GemmaModel.FAST) is EngineState.ModelMissing)

        val sets = m.parseQuickLog("4x10 planche lean 5s pausa").toList()
        assertEquals(ExerciseModality.ISOMETRIC, sets.single().modality)
    }

    @Test
    fun `carga en GPU y emite sets desde el stream del modelo`() = runTest {
        val m = manager(
            this,
            resourcesWith(GemmaModel.FAST),
            chunks = listOf("{\"exercise\":\"muscle up\",", "\"modality\":\"bodyweight\",\"sets\":5,\"reps\":3}"),
        )
        val state = m.load(GemmaModel.FAST)
        assertTrue(state is EngineState.Ready && state.backend == ComputeBackend.GPU)

        assertEquals("muscle up", m.parseQuickLog("5x3 muscle up").toList().single().exercise)
    }

    @Test
    fun `si el delegado GPU falla cae a CPU y cierra el motor fallido`() = runTest {
        val created = mutableListOf<FakeEngine>()
        val m = manager(this, resourcesWith(GemmaModel.FAST), failOn = ComputeBackend.GPU, created = created)
        val state = m.load(GemmaModel.FAST)
        assertTrue(state is EngineState.Ready && state.backend == ComputeBackend.CPU)
        assertTrue(created.first().closed)
    }

    @Test
    fun `RAM insuficiente bloquea la carga del modelo grande`() = runTest {
        val m = manager(this, resourcesWith(GemmaModel.COMPLEX, ram = 3_000))
        assertTrue(m.load(GemmaModel.COMPLEX) is EngineState.Error)
    }

    @Test
    fun `cambiar de modelo libera el anterior`() = runTest {
        val created = mutableListOf<FakeEngine>()
        val m = manager(this, resourcesWith(GemmaModel.FAST, GemmaModel.COMPLEX), created = created)
        m.load(GemmaModel.FAST)
        m.load(GemmaModel.COMPLEX)
        assertTrue(created[0].closed)
        assertEquals(GemmaModel.COMPLEX, (m.state.value as EngineState.Ready).model)
    }

    @Test
    fun `salida sin JSON valido cae al parser heuristico`() = runTest {
        val m = manager(this, resourcesWith(GemmaModel.FAST), chunks = listOf("Lo siento, no entiendo."))
        m.load(GemmaModel.FAST)
        assertEquals("dominadas", m.parseQuickLog("dominadas 3x8").toList().single().exercise)
    }

    @Test
    fun `modelo truncado no se carga`() = runTest {
        val resources = resourcesWith()
        File(resources.modelsDir, GemmaModel.FAST.fileName).writeText("push interrumpido")
        val created = mutableListOf<FakeEngine>()
        val m = manager(this, resources, created = created)
        assertTrue(m.load(GemmaModel.FAST) is EngineState.Error)
        assertTrue(created.isEmpty())
    }

    @Test
    fun `unload espera a que termine la generacion en curso`() = runTest {
        val created = mutableListOf<FakeEngine>()
        val m = manager(
            this,
            resourcesWith(GemmaModel.FAST),
            chunks = listOf("{\"exercise\":\"dips\",", "\"modality\":\"bodyweight\",\"sets\":3,\"reps\":10}"),
            created = created,
            chunkDelayMs = 1_000,
        )
        m.load(GemmaModel.FAST)
        val generation = launch { m.parseQuickLog("dips 3x10").toList() }
        advanceTimeBy(500)
        runCurrent()

        val unload = launch { m.unload() }
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse("No se puede cerrar el motor a mitad de stream", created.single().closed)

        advanceUntilIdle()
        assertTrue(generation.isCompleted && unload.isCompleted)
        assertTrue(created.single().closed)
        assertEquals(EngineState.Unloaded, m.state.value)
    }

    @Test
    fun `en segundo plano el modelo rapido se conserva durante el margen`() = runTest {
        val created = mutableListOf<FakeEngine>()
        val m = manager(this, resourcesWith(GemmaModel.FAST), created = created)
        m.loadAsync(GemmaModel.FAST)
        advanceUntilIdle()

        m.onAppBackgrounded()
        advanceTimeBy(GemmaModel.FAST.backgroundGrace - 1.seconds)
        runCurrent()
        assertTrue(m.state.value is EngineState.Ready)

        // Volver antes del margen cancela la descarga.
        m.onAppForegrounded()
        advanceTimeBy(10.minutes)
        runCurrent()
        assertTrue(m.state.value is EngineState.Ready)
        assertFalse(created.single().closed)
    }

    @Test
    fun `el modelo grande se libera al pasar a segundo plano y se recarga al volver`() = runTest {
        val created = mutableListOf<FakeEngine>()
        val m = manager(this, resourcesWith(GemmaModel.COMPLEX), created = created)
        m.loadAsync(GemmaModel.COMPLEX)
        advanceUntilIdle()

        m.onAppBackgrounded()
        advanceUntilIdle()
        assertEquals(EngineState.Unloaded, m.state.value)
        assertTrue(created.single().closed)

        m.onAppForegrounded()
        advanceUntilIdle()
        assertEquals(GemmaModel.COMPLEX, (m.state.value as EngineState.Ready).model)
    }
}
