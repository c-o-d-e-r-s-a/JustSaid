package com.justsaid.app.summary

import com.justsaid.app.core.Transcript

/**
 * Last-resort extractor used only when the LLM returns nothing usable.
 * Finds clear first-person commitments already present in the transcript and
 * proposes them with the matched text as the proof quote — so
 * [HallucinationGuard] can still enforce Constitution G1 (no invented quotes).
 *
 * Deliberately narrow: only "I will / I'll / I am going to / I'm going to"
 * clauses. Prefer the model; this exists because 1B-class summarizers often
 * emit `NONE` or off-format prose on short mono transcripts.
 */
object CommitmentFallback {

    private val COMMITMENT = Regex(
        """\b((?:I will|I'll|I am going to|I'm going to)\s+.+?)(?=\s+and\s+(?:I will|I'll|I am going to|I'm going to)\b|[.!?]|\s*$|$)""",
        RegexOption.IGNORE_CASE,
    )

    fun propose(transcript: Transcript): List<ParsedPromise> {
        val spoken = transcript.segments.joinToString(" ") { it.text.trim() }
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (spoken.isEmpty()) return emptyList()

        return COMMITMENT.findAll(spoken).mapNotNull { match ->
            val quote = match.groupValues[1].trim().trimEnd(',', ';', ' ')
            if (quote.length < 8) return@mapNotNull null
            ParsedPromise(
                task = summarizeTask(quote),
                quantity = null,
                proofQuote = quote,
                markedUnconfirmed = true,
            )
        }.distinctBy { normalize(it.proofQuote) }.toList()
    }

    /** Short display label derived from the quote; the quote itself is the proof. */
    private fun summarizeTask(quote: String): String {
        val stripped = quote
            .replace(Regex("""^(?:I will|I'll|I am going to|I'm going to)\s+""", RegexOption.IGNORE_CASE), "")
            .trim()
            .trimEnd('.')
        if (stripped.isEmpty()) return quote
        return stripped.replaceFirstChar { it.uppercase() }
    }

    private fun normalize(text: String) = text.lowercase().replace(Regex("""\s+"""), " ").trim()
}
