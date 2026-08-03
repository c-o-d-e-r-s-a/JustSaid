package com.justsaid.app.llm

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.audio.CaptureInput
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.ModelPaths
import com.justsaid.app.core.Speaker
import com.justsaid.app.core.Transcript
import com.justsaid.app.core.TranscriptSegment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Chunked summarization without JNI: fake [NativeLlmBridge] returns per-chunk output;
 * guardrails validate quotes against each chunk only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LlamaSummarizerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeModelPaths(private val llm: File) : ModelPaths {
        override fun sttModelFile(): File = llm
        override fun llmModelFile(): File = llm
        override fun modelsReady(): Boolean = llm.exists()
    }

    private class QueueBridge(
        private val outputs: ArrayDeque<String>,
    ) : NativeLlmBridge {
        var generateCalls = 0

        override fun init(modelPath: String, threads: Int, nGpuLayers: Int): Long = 1L

        override fun generate(handle: Long, prompt: String, maxTokens: Int, temp: Float): String {
            generateCalls++
            return outputs.removeFirst()
        }

        override fun free(handle: Long) = Unit
    }

    private fun summarizer(
        bridge: QueueBridge,
        maxChars: Int = 80,
    ): LlamaSummarizer {
        val model = tmp.newFile("llm.gguf")
        val engine = LlmEngine(
            modelPaths = FakeModelPaths(model),
            dispatcher = UnconfinedTestDispatcher(),
            params = LlmParams(maxTranscriptCharsPerChunk = maxChars),
            bridgeOverride = bridge,
        )
        return LlamaSummarizer(engine, LlmParams(maxTranscriptCharsPerChunk = maxChars)) { 1_000L }
    }

    private fun session() = RecordedSession(
        id = "session-1",
        wavFile = tmp.newFile("s.wav"),
        input = CaptureInput.MICROPHONE_MONO,
        sampleRate = 16_000,
        channels = 1,
        startedAt = 500L,
        durationMs = 10_000L,
        sessionLabel = "test",
    )

    private fun longTranscript(): Transcript {
        val segments = listOf(
            TranscriptSegment(Speaker.UNKNOWN, "I will buy milk tomorrow morning", 0, 900),
            TranscriptSegment(Speaker.UNKNOWN, "and I will fix the sink this weekend", 1_000, 1_900),
        )
        return Transcript(segments, detectedLanguage = "en")
    }

    @Test
    fun `long transcript triggers chunked generation and merges validated items`() = runTest {
        val bridge = QueueBridge(
            ArrayDeque(
                listOf(
                    """Unconfirmed: Buy milk (Proof: "I will buy milk tomorrow morning")""",
                    """Unconfirmed: Fix sink (Proof: "I will fix the sink this weekend")""",
                ),
            ),
        )
        val result = summarizer(bridge, maxChars = 60).summarize(session(), longTranscript())

        assertThat(bridge.generateCalls).isEqualTo(2)
        val summary = (result as JustSaidResult.Success).value
        assertThat(summary.items).hasSize(2)
        summary.items.forEach { item ->
            assertThat(item.attributedTo).isEqualTo(Speaker.UNKNOWN)
            assertThat(item.confirmed).isFalse()
        }
    }

    @Test
    fun `quote validated only against its chunk is dropped when not in chunk`() = runTest {
        val transcript = Transcript(
            listOf(
                TranscriptSegment(Speaker.UNKNOWN, "please bring cake on Friday", 0, 900),
                TranscriptSegment(Speaker.UNKNOWN, "and send the invoice by Monday", 1_000, 1_900),
            ),
            detectedLanguage = "es",
        )
        val bridge = QueueBridge(
            ArrayDeque(
                listOf(
                    """Unconfirmed: Hire clown (Proof: "I will hire a clown")""",
                    "NONE",
                ),
            ),
        )
        val result = summarizer(bridge, maxChars = 60).summarize(session(), transcript)

        val summary = (result as JustSaidResult.Success).value
        assertThat(summary.items).isEmpty()
    }

    @Test
    fun `duplicate proof quotes across chunks are deduped`() = runTest {
        val transcript = Transcript(
            listOf(
                TranscriptSegment(Speaker.UNKNOWN, "I will send the report tonight", 0, 900),
                TranscriptSegment(Speaker.UNKNOWN, "please confirm I will send the report tonight", 1_000, 1_900),
            ),
            detectedLanguage = "en",
        )
        val line = """Unconfirmed: Send report (Proof: "I will send the report tonight")"""
        val bridge = QueueBridge(ArrayDeque(listOf(line, line)))

        val summary = (summarizer(bridge, maxChars = 60).summarize(session(), transcript) as JustSaidResult.Success).value

        assertThat(summary.items).hasSize(1)
    }

    @Test
    fun `llm failure on any chunk returns user-safe Failure`() = runTest {
        val engine = LlmEngine(
            modelPaths = FakeModelPaths(tmp.newFile("llm.gguf")),
            dispatcher = UnconfinedTestDispatcher(),
            params = LlmParams(maxTranscriptCharsPerChunk = 60),
            bridgeOverride = object : NativeLlmBridge {
                override fun init(modelPath: String, threads: Int, nGpuLayers: Int): Long = 1L
                override fun generate(handle: Long, prompt: String, maxTokens: Int, temp: Float): String = ""
                override fun free(handle: Long) = Unit
            },
        )
        val result = LlamaSummarizer(engine, LlmParams(maxTranscriptCharsPerChunk = 60)) { 1_000L }
            .summarize(session(), longTranscript())

        assertThat(result).isInstanceOf(JustSaidResult.Failure::class.java)
    }
}
