package com.justsaid.app.llm

import com.justsaid.app.audio.RecordedCall
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.Transcript
import com.justsaid.app.summary.CommitmentFallback
import com.justsaid.app.summary.HallucinationGuard
import com.justsaid.app.summary.PromiseParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real llama.cpp summarizer: renders the transcript, runs one generation
 * through [LlmEngine], then passes the untrusted model output through the
 * code-enforced guardrails ([PromiseParser] → [HallucinationGuard], Constitution
 * G1). A summary with zero surviving items is still a Success — "nothing
 * actionable" is a valid outcome (the prompt's `NONE` case), not an error.
 */
@Singleton
class LlamaSummarizer(
    private val engine: LlmEngine,
    private val now: () -> Long,
) : LlmSummarizer {

    @Inject
    constructor(engine: LlmEngine) : this(engine, System::currentTimeMillis)

    override suspend fun summarize(
        call: RecordedCall,
        transcript: Transcript,
    ): JustSaidResult<CallSummary> {
        val transcriptText = transcript.plainText()
        if (transcriptText.isBlank()) {
            return JustSaidResult.Failure("transcript is empty")
        }

        val output = when (val result = engine.generate(Prompts.forTranscript(transcriptText))) {
            is JustSaidResult.Success -> result.value
            is JustSaidResult.Failure -> return result
        }

        var items = HallucinationGuard.validate(PromiseParser.parse(output), transcript)
        // 1B-class models often emit NONE / off-format prose on short mono calls
        // even when the transcript clearly contains "I will …" commitments. Fall
        // back to a quote-preserving extractor; HallucinationGuard still drops
        // anything that is not a literal substring (Constitution G1).
        if (items.isEmpty()) {
            items = HallucinationGuard.validate(CommitmentFallback.propose(transcript), transcript)
        }

        return JustSaidResult.Success(
            CallSummary(
                id = 0L, // storage id assigned by SummaryRepo.save
                contactName = call.contactName,
                phoneNumber = call.phoneNumber,
                createdAt = now(),
                items = items,
                fullTranscript = transcriptText,
            ),
        )
    }
}
