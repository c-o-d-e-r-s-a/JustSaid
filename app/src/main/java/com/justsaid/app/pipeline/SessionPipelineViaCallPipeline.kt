package com.justsaid.app.pipeline

import com.justsaid.app.audio.CallPipeline
import com.justsaid.app.audio.CaptureTier
import com.justsaid.app.audio.RecordedCall
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.audio.SessionPipeline
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.core.JustSaidResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temporary bridge until task 3/4 migrate STT/LLM to [RecordedSession]. WAV deletion
 * remains in [CallPipelineImpl.finally].
 */
@Singleton
class SessionPipelineViaCallPipeline @Inject constructor(
  private val callPipeline: CallPipeline,
  @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : SessionPipeline {

  override suspend fun process(session: RecordedSession): JustSaidResult<CallSummary> =
    withContext(dispatcher) {
      val call = RecordedCall(
        wavFile = session.wavFile,
        tier = CaptureTier.MIC_ONLY,
        sampleRate = session.sampleRate,
        channels = session.channels,
        phoneNumber = session.id,
        contactName = session.sessionLabel,
        durationMs = session.durationMs,
      )
      try {
        callPipeline.process(call)
        JustSaidResult.Success(
          CallSummary(
            id = 0L,
            contactName = session.sessionLabel,
            phoneNumber = session.id,
            createdAt = session.startedAt,
            items = emptyList(),
            fullTranscript = "",
          ),
        )
      } catch (e: Exception) {
        JustSaidResult.Failure(e.message ?: "processing failed", e)
      }
    }
}
