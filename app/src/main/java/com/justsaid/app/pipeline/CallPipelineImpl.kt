package com.justsaid.app.pipeline

import com.justsaid.app.audio.CallPipeline
import com.justsaid.app.audio.CaptureInput
import com.justsaid.app.audio.RecordedCall
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.data.repo.SummaryRepo
import com.justsaid.app.llm.LlmSummarizer
import com.justsaid.app.stt.SttPipeline
import com.justsaid.app.summary.SummaryEvents
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The full post-call pipeline Phase 4 assembles: STT → LLM summarize (guardrailed)
 * → persist → publish for the summary screen.
 *
 * This is the ONE place the raw `.wav` is deleted — in `finally`, so the
 * radioactive buffer dies on success, on STT/LLM failure, and on any exception
 * (Constitution A1/E2). Neither [SttPipeline] nor [LlmSummarizer] ever touches
 * the file's lifecycle.
 */
@Singleton
class CallPipelineImpl @Inject constructor(
    private val stt: SttPipeline,
    private val summarizer: LlmSummarizer,
    private val repo: SummaryRepo,
    private val summaryEvents: SummaryEvents,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : CallPipeline {

    override suspend fun process(call: RecordedCall): Unit = withContext(dispatcher) {
        try {
            val transcript = when (val r = stt.transcribe(call)) {
                is JustSaidResult.Success -> r.value
                is JustSaidResult.Failure -> return@withContext // E2: wav still deleted below
            }
            val summary = when (
                val r = summarizer.summarize(call.toSession(), transcript)
            ) {
                is JustSaidResult.Success -> r.value
                is JustSaidResult.Failure -> return@withContext
            }
            // Persist first so the published summary carries its storage id.
            val saved = when (val r = repo.save(summary)) {
                is JustSaidResult.Success -> r.value
                is JustSaidResult.Failure -> summary
            }
            summaryEvents.publish(saved)
        } finally {
            // [CRITICAL A1] single deletion point, success or fail.
            call.wavFile.delete()
        }
    }

    private fun RecordedCall.toSession() = RecordedSession(
        id = phoneNumber,
        wavFile = wavFile,
        input = CaptureInput.MICROPHONE_MONO,
        sampleRate = sampleRate,
        channels = channels,
        startedAt = 0L,
        durationMs = durationMs,
        sessionLabel = contactName,
    )
}
