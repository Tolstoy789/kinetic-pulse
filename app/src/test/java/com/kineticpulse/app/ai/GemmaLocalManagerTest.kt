package com.kineticpulse.app.ai

import com.kineticpulse.core.model.ExerciseModality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
    ) : LlmEngine {
        var closed = false
        override fun initialize() {
            if (backend == failOn) throw IllegalStateException("OpenCL no disponible")
        }
        override fun generate(systemInstruction: String, prompt: String, temperature: Float): Flow<String> =
            chunks.asFlow()
        override fun close() { closed = true }
    }

    private fun manager(
        scope: TestScope,
        resources: DeviceResources,
        chunks: List<String> = emptyList(),
        failOn: ComputeBackend? = null,
        created: MutableList<FakeEngine> = mutableListOf(),
    ): GemmaLocalManager {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return GemmaLocalManager(
            resources = resources,
            engineFactory = { _, backend, _ -> FakeEngine(chunks, failOn, backend).also(created::add) },
            scope = scope,
            ioDispatcher = dispatcher,
        )
    }

    private fun resourcesWith(vararg models: GemmaModel, ram: Long = 8_000): FakeResources {
        val dir = tmp.newFolder("models")
        models.forEach { File(dir, it.fileName).writeText("stub") }
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
}
