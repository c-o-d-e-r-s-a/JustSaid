package com.justsaid.app.llm

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.ModelPaths
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * TESTING.md C.1: [LlmEngine]'s Kotlin contract through a fake [NativeLlmBridge] —
 * no `.so`, no device. Covers the lifecycle discipline (init once → generate →
 * free exactly once, including on exception paths) and every Failure branch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LlmEngineFakeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeModelPaths(private val llm: File) : ModelPaths {
        override fun sttModelFile(): File = llm
        override fun llmModelFile(): File = llm
        override fun modelsReady(): Boolean = llm.exists()
    }

    private class FakeBridge(
        var initResult: Long = 42L,
        var generateResult: String = "NONE",
        var generateThrows: Throwable? = null,
    ) : NativeLlmBridge {
        var initCalls = 0
        var generateCalls = 0
        val freedHandles = mutableListOf<Long>()
        var lastPrompt: String? = null
        var lastMaxTokens = 0
        var lastTemp = 0f
        var lastThreads = 0
        var lastGpuLayers = 0

        override fun init(modelPath: String, threads: Int, nGpuLayers: Int): Long {
            initCalls++
            lastThreads = threads
            lastGpuLayers = nGpuLayers
            return initResult
        }

        override fun generate(handle: Long, prompt: String, maxTokens: Int, temp: Float): String {
            generateCalls++
            lastPrompt = prompt
            lastMaxTokens = maxTokens
            lastTemp = temp
            generateThrows?.let { throw it }
            return generateResult
        }

        override fun free(handle: Long) {
            freedHandles += handle
        }
    }

    private fun engine(bridge: FakeBridge, modelFile: File, params: LlmParams = LlmParams()) =
        LlmEngine(
            modelPaths = FakeModelPaths(modelFile),
            dispatcher = UnconfinedTestDispatcher(),
            params = params,
            bridgeOverride = bridge,
        )

    private fun existingModel(): File = tmp.newFile("llama-test.gguf")

    @Test
    fun `missing model file fails without touching native`() = runTest {
        val bridge = FakeBridge()
        val result = engine(bridge, File(tmp.root, "absent.gguf")).generate("prompt")

        assertThat(result).isInstanceOf(JustSaidResult.Failure::class.java)
        assertThat(bridge.initCalls).isEqualTo(0)
    }

    @Test
    fun `init failure returns Failure and never generates or frees`() = runTest {
        val bridge = FakeBridge(initResult = 0L)
        val result = engine(bridge, existingModel()).generate("prompt")

        assertThat(result).isInstanceOf(JustSaidResult.Failure::class.java)
        assertThat(bridge.generateCalls).isEqualTo(0)
        assertThat(bridge.freedHandles).isEmpty()
    }

    @Test
    fun `successful generation returns text and frees the handle once`() = runTest {
        val bridge = FakeBridge(generateResult = """Buy milk (Proof: "I'll buy milk")""")
        val result = engine(bridge, existingModel()).generate("prompt")

        assertThat((result as JustSaidResult.Success).value).contains("Buy milk")
        assertThat(bridge.initCalls).isEqualTo(1)
        assertThat(bridge.freedHandles).containsExactly(42L)
    }

    @Test
    fun `blank native output is a Failure but still frees`() = runTest {
        val bridge = FakeBridge(generateResult = "")
        val result = engine(bridge, existingModel()).generate("prompt")

        assertThat(result).isInstanceOf(JustSaidResult.Failure::class.java)
        assertThat(bridge.freedHandles).containsExactly(42L)
    }

    @Test
    fun `exception during generation becomes Failure and still frees`() = runTest {
        val bridge = FakeBridge(generateThrows = IllegalStateException("boom"))
        val result = engine(bridge, existingModel()).generate("prompt")

        assertThat(result).isInstanceOf(JustSaidResult.Failure::class.java)
        assertThat((result as JustSaidResult.Failure).cause).isInstanceOf(IllegalStateException::class.java)
        assertThat(bridge.freedHandles).containsExactly(42L)
    }

    @Test
    fun `params flow through to the bridge`() = runTest {
        val bridge = FakeBridge()
        val params = LlmParams(maxTokens = 256, temperature = 0.2f, gpuOffloadLayers = 0)
        engine(bridge, existingModel(), params).generate("the prompt")

        assertThat(bridge.lastPrompt).isEqualTo("the prompt")
        assertThat(bridge.lastMaxTokens).isEqualTo(256)
        assertThat(bridge.lastTemp).isEqualTo(0.2f)
        assertThat(bridge.lastGpuLayers).isEqualTo(0)
        assertThat(bridge.lastThreads).isAtLeast(2)
    }

    @Test
    fun `params reject temperature above the G4 ceiling`() {
        try {
            LlmParams(temperature = 0.7f)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("G4")
        }
    }
}
