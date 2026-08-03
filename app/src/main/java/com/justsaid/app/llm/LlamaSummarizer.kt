package com.justsaid.app.llm

import com.justsaid.app.audio.CaptureInput
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.Transcript
import com.justsaid.app.summary.CommitmentFallback
import com.justsaid.app.summary.HallucinationGuard
import com.justsaid.app.summary.PromiseDeduper
import com.justsaid.app.summary.PromiseParser
import com.justsaid.app.summary.TranscriptChunker
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real llama.cpp summarizer: chunks long transcripts, runs one generation
 * per bounded chunk through [LlmEngine], validates each item against its source
 * chunk, then merges and deduplicates. Companion microphone sessions mark every
 * surviving item unconfirmed (Constitution S2).
 */
@Singleton
class LlamaSummarizer(
    private val engine: LlmEngine,
    private val params: LlmParams,
    private val now: () -> Long,
) : LlmSummarizer {

    @Inject
    constructor(engine: LlmEngine) : this(engine, LlmParams(), System::currentTimeMillis)

    override suspend fun summarize(
        session: RecordedSession,
        transcript: Transcript,
    ): JustSaidResult<CallSummary> {
        val transcriptText = transcript.plainText()
        if (transcriptText.isBlank()) {
            return JustSaidResult.Failure("transcript is empty")
        }

        val micSession = session.input == CaptureInput.MICROPHONE_MONO
        val chunks = TranscriptChunker.chunks(transcript, params.maxTranscriptCharsPerChunk)
        if (chunks.isEmpty()) {
            return JustSaidResult.Failure("transcript is empty")
        }

        val merged = mutableListOf<com.justsaid.app.core.PromiseItem>()
        for (chunk in chunks) {
            val chunkText = chunk.plainText()
            val output = when (
                val result = engine.generate(
                    Prompts.forTranscript(chunkText, chunk.detectedLanguage),
                )
            ) {
                is JustSaidResult.Success -> result.value
                is JustSaidResult.Failure -> return result
            }

            var items = HallucinationGuard.validate(
                PromiseParser.parse(output),
                chunk,
                micSession = micSession,
            )
            if (items.isEmpty() && CommitmentFallback.appliesTo(chunk)) {
                items = HallucinationGuard.validate(
                    CommitmentFallback.propose(chunk),
                    chunk,
                    micSession = micSession,
                )
            }
            merged += items
        }

        return JustSaidResult.Success(
            CallSummary(
                id = 0L,
                sessionLabel = session.sessionLabel,
                createdAt = now(),
                items = PromiseDeduper.merge(merged),
                fullTranscript = transcriptText,
            ),
        )
    }
}
