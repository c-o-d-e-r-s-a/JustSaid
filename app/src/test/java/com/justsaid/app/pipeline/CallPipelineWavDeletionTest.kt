package com.justsaid.app.pipeline

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.audio.CaptureTier
import com.justsaid.app.audio.CaptureInput
import com.justsaid.app.audio.RecordedCall
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.Speaker
import com.justsaid.app.core.Transcript
import com.justsaid.app.core.TranscriptSegment
import com.justsaid.app.data.repo.SummaryRepo
import com.justsaid.app.llm.LlmSummarizer
import com.justsaid.app.stt.SttPipeline
import com.justsaid.app.summary.SummaryEvents
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * TESTING.md C.1 (`AudioLifecycleTest` requirement) [CRITICAL A1]: the raw wav is
 * deleted by the pipeline's single delete-in-finally point on EVERY exit —
 * success, STT/LLM soft failure, and thrown exception. Also verifies the happy
 * path publishes the persisted summary (with its storage id) to [SummaryEvents].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallPipelineWavDeletionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val stt = mockk<SttPipeline>()
    private val summarizer = mockk<LlmSummarizer>()
    private val events = SummaryEvents()

    private val savingRepo = object : SummaryRepo {
        var saved: CallSummary? = null
        override suspend fun save(summary: CallSummary): JustSaidResult<CallSummary> {
            val withId = summary.copy(id = 7L)
            saved = withId
            return JustSaidResult.Success(withId)
        }
    }

    private fun pipeline() = CallPipelineImpl(
        stt = stt,
        summarizer = summarizer,
        repo = savingRepo,
        summaryEvents = events,
        dispatcher = UnconfinedTestDispatcher(),
    )

    private fun recordedCall(): RecordedCall {
        val wav = tmp.newFile("call.wav").apply { writeBytes(ByteArray(64)) }
        return RecordedCall(
            wavFile = wav,
            tier = CaptureTier.MIC_ONLY,
            sampleRate = 16_000,
            channels = 1,
            phoneNumber = "+15555550123",
            contactName = "Ada",
            durationMs = 30_000L,
        )
    }

    private val transcript = Transcript(
        listOf(TranscriptSegment(Speaker.LOCAL, "I'll buy milk", 0L, 900L)),
    )

    private fun summaryFor(call: RecordedCall) = CallSummary(
        id = 0L,
        contactName = call.contactName,
        phoneNumber = call.phoneNumber,
        createdAt = 123L,
        items = emptyList(),
        fullTranscript = transcript.plainText(),
    )

    private fun callToSession(call: RecordedCall) = RecordedSession(
        id = call.phoneNumber,
        wavFile = call.wavFile,
        input = CaptureInput.MICROPHONE_MONO,
        sampleRate = call.sampleRate,
        channels = call.channels,
        startedAt = 0L,
        durationMs = call.durationMs,
        sessionLabel = call.contactName,
    )

    @Test
    fun `wav deleted on success and summary published with storage id`() = runTest {
        val call = recordedCall()
        coEvery { stt.transcribe(call) } returns JustSaidResult.Success(transcript)
        coEvery { summarizer.summarize(callToSession(call), transcript) } returns
            JustSaidResult.Success(summaryFor(call))

        pipeline().process(call)

        assertThat(call.wavFile.exists()).isFalse()
        assertThat(savingRepo.saved).isNotNull()
        assertThat(events.latest.value?.id).isEqualTo(7L)
    }

    @Test
    fun `wav deleted when STT returns Failure`() = runTest {
        val call = recordedCall()
        coEvery { stt.transcribe(call) } returns JustSaidResult.Failure("stt broke")

        pipeline().process(call)

        assertThat(call.wavFile.exists()).isFalse()
        assertThat(events.latest.value).isNull()
    }

    @Test
    fun `wav deleted when summarizer returns Failure`() = runTest {
        val call = recordedCall()
        coEvery { stt.transcribe(call) } returns JustSaidResult.Success(transcript)
        coEvery { summarizer.summarize(callToSession(call), transcript) } returns JustSaidResult.Failure("llm broke")

        pipeline().process(call)

        assertThat(call.wavFile.exists()).isFalse()
        assertThat(events.latest.value).isNull()
    }

    @Test
    fun `wav deleted when STT throws`() = runTest {
        val call = recordedCall()
        coEvery { stt.transcribe(call) } throws IllegalStateException("native crash surfaced")

        val thrown = runCatching { pipeline().process(call) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        assertThat(call.wavFile.exists()).isFalse()
    }

    @Test
    fun `wav deleted when summarizer throws`() = runTest {
        val call = recordedCall()
        coEvery { stt.transcribe(call) } returns JustSaidResult.Success(transcript)
        coEvery { summarizer.summarize(callToSession(call), transcript) } throws RuntimeException("boom")

        runCatching { pipeline().process(call) }

        assertThat(call.wavFile.exists()).isFalse()
    }
}
