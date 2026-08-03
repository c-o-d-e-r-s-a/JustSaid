package com.justsaid.app.pipeline

import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.audio.SessionPipeline
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.llm.LlmSummarizer
import com.justsaid.app.session.SessionWavFiles
import com.justsaid.app.stt.SttPipeline
import com.justsaid.app.summary.PendingSummaryHandoff
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Post-capture companion pipeline: STT → bounded LLM summarize (guardrailed) →
 * hand off an unsaved summary for the summary screen. Does not persist text.
 *
 * This is the ONE place the raw `.wav` is deleted — in `finally`, so the
 * radioactive buffer dies on success, on STT/LLM failure, and on any exception
 * (Constitution A1/E2).
 */
@Singleton
class SessionPipelineImpl @Inject constructor(
    private val stt: SttPipeline,
    private val summarizer: LlmSummarizer,
    private val summaryHandoff: PendingSummaryHandoff,
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result = JustSaidResult.Failure(PROCESSING_FAILED, e)
            } finally {
                SessionWavFiles.deleteVerified(session.wavFile)
            }
            afterWavDeletion(session.wavFile, result)
        }

    private fun afterWavDeletion(
        wavFile: java.io.File,
        result: JustSaidResult<CallSummary>?,
    ): JustSaidResult<CallSummary> {
        val outcome = wavDeletionOutcome(wavFile.exists(), result)
        if (outcome is JustSaidResult.Success && outcome.value.id == 0L) {
            summaryHandoff.publish(outcome.value)
        }
        return outcome
    }

    internal companion object {
        const val PROCESSING_FAILED: String = "Could not finish processing."

        fun wavDeletionOutcome(
            wavStillPresent: Boolean,
            result: JustSaidResult<CallSummary>?,
        ): JustSaidResult<CallSummary> {
            if (!wavStillPresent) {
                return result ?: JustSaidResult.Failure(PROCESSING_FAILED)
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
