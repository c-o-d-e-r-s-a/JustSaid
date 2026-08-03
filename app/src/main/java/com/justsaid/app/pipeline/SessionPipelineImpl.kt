package com.justsaid.app.pipeline

import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.audio.SessionPipeline
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.llm.LlmSummarizer
import com.justsaid.app.stt.SttPipeline
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Post-capture companion pipeline: STT → bounded LLM summarize (guardrailed) →
 * return summary for a later UI retention decision. Does not persist text.
 *
 * This is the ONE place the raw `.wav` is deleted — in `finally`, so the
 * radioactive buffer dies on success, on STT/LLM failure, and on any exception
 * (Constitution A1/E2).
 */
@Singleton
class SessionPipelineImpl @Inject constructor(
    private val stt: SttPipeline,
    private val summarizer: LlmSummarizer,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : SessionPipeline {

    override suspend fun process(session: RecordedSession): JustSaidResult<CallSummary> =
        withContext(dispatcher) {
            var result: JustSaidResult<CallSummary>? = null
            try {
                result = when (val sttResult = stt.transcribe(session)) {
                    is JustSaidResult.Success -> summarizer.summarize(session, sttResult.value)
                    is JustSaidResult.Failure -> sttResult
                }
            } finally {
                session.wavFile.delete()
            }
            afterWavDeletion(session, result)
        }

    private fun afterWavDeletion(
        session: RecordedSession,
        result: JustSaidResult<CallSummary>?,
    ): JustSaidResult<CallSummary> = wavDeletionOutcome(session.wavFile.exists(), result)

    internal companion object {
        fun wavDeletionOutcome(
            wavStillPresent: Boolean,
            result: JustSaidResult<CallSummary>?,
        ): JustSaidResult<CallSummary> {
            if (!wavStillPresent) {
                return result ?: JustSaidResult.Failure("processing failed")
            }
            return when (result) {
                is JustSaidResult.Success ->
                    JustSaidResult.Failure("Summary ready but temporary audio could not be deleted.")
                is JustSaidResult.Failure ->
                    JustSaidResult.Failure("${result.reason}; temporary audio could not be deleted.", result.cause)
                null -> JustSaidResult.Failure("Temporary audio could not be deleted.")
            }
        }
    }
}
