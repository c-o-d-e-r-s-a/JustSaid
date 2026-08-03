package com.justsaid.app.pipeline

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.audio.CaptureInput
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.Speaker
import com.justsaid.app.core.Transcript
import com.justsaid.app.core.TranscriptSegment
import com.justsaid.app.llm.LlmSummarizer
import com.justsaid.app.stt.SttPipeline
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
 * TESTING.md C.1 [CRITICAL A1]: [SessionPipelineImpl] deletes the raw wav on every
 * exit and surfaces deletion failure without auto-saving text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionPipelineWavDeletionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val stt = mockk<SttPipeline>()
    private val summarizer = mockk<LlmSummarizer>()

    private fun pipeline() = SessionPipelineImpl(
        stt = stt,
        summarizer = summarizer,
        dispatcher = UnconfinedTestDispatcher(),
    )

    private fun recordedSession(wav: File): RecordedSession = RecordedSession(
        id = "session-abc",
        wavFile = wav,
        input = CaptureInput.MICROPHONE_MONO,
        sampleRate = 16_000,
        channels = 1,
        startedAt = 100L,
        durationMs = 5_000L,
        sessionLabel = "meeting",
    )

    private val transcript = Transcript(
        listOf(TranscriptSegment(Speaker.UNKNOWN, "I'll buy milk", 0L, 900L)),
    )

    private fun summaryFor(session: RecordedSession) = CallSummary(
        id = 0L,
        contactName = session.sessionLabel,
        phoneNumber = session.id,
        createdAt = 123L,
        items = emptyList(),
        fullTranscript = transcript.plainText(),
    )

    @Test
    fun `wav deleted on success and summary returned without persisting`() = runTest {
        val session = recordedSession(tmp.newFile("call.wav").apply { writeBytes(ByteArray(64)) })
        coEvery { stt.transcribe(session) } returns JustSaidResult.Success(transcript)
        coEvery { summarizer.summarize(session, transcript) } returns
            JustSaidResult.Success(summaryFor(session))

        val result = pipeline().process(session)

        assertThat(session.wavFile.exists()).isFalse()
        assertThat(result).isInstanceOf(JustSaidResult.Success::class.java)
        assertThat((result as JustSaidResult.Success).value.id).isEqualTo(0L)
    }

    @Test
    fun `wav deleted when STT returns Failure`() = runTest {
        val session = recordedSession(tmp.newFile("call.wav").apply { writeBytes(ByteArray(64)) })
        coEvery { stt.transcribe(session) } returns JustSaidResult.Failure("stt broke")

        pipeline().process(session)

        assertThat(session.wavFile.exists()).isFalse()
    }

    @Test
    fun `wav deleted when summarizer returns Failure`() = runTest {
        val session = recordedSession(tmp.newFile("call.wav").apply { writeBytes(ByteArray(64)) })
        coEvery { stt.transcribe(session) } returns JustSaidResult.Success(transcript)
        coEvery { summarizer.summarize(session, transcript) } returns JustSaidResult.Failure("llm broke")

        pipeline().process(session)

        assertThat(session.wavFile.exists()).isFalse()
    }

    @Test
    fun `wav deleted when STT throws`() = runTest {
        val session = recordedSession(tmp.newFile("call.wav").apply { writeBytes(ByteArray(64)) })
        coEvery { stt.transcribe(session) } throws IllegalStateException("native crash surfaced")

        val thrown = runCatching { pipeline().process(session) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        assertThat(session.wavFile.exists()).isFalse()
    }

    @Test
    fun `deletion failure is reported when wav survives cleanup`() {
        val summary = summaryFor(
            recordedSession(tmp.newFile("call.wav")),
        )
        val result = SessionPipelineImpl.wavDeletionOutcome(
            wavStillPresent = true,
            result = JustSaidResult.Success(summary),
        )

        assertThat(result).isInstanceOf(JustSaidResult.Failure::class.java)
        assertThat((result as JustSaidResult.Failure).reason)
            .contains("temporary audio could not be deleted")
    }
}
