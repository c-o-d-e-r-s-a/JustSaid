package com.justsaid.app.summary

import com.justsaid.app.core.PromiseItem
import com.justsaid.app.core.Speaker
import com.justsaid.app.core.Transcript

/**
 * The code-enforced side of Constitution G1: an LLM output line only becomes a
 * [PromiseItem] if its proof quote is a literal substring of the transcript after
 * normalization (lowercase, collapsed whitespace, punctuation-trimmed quote edges).
 * Items that fail are DROPPED, never repaired or guessed. Strict by design — no
 * fuzzy matching.
 *
 * Attribution is also derived here from the transcript, not from the model: the
 * quote is located in each speaker's own text (S1/S2). If it cannot be pinned to
 * exactly one known speaker, or the model itself flagged the line, the item is
 * kept but marked `confirmed=false` (rendered "Unconfirmed").
 */
object HallucinationGuard {

    fun validate(parsed: List<ParsedPromise>, transcript: Transcript): List<PromiseItem> {
        val haystack = normalize(transcript.plainText())
        if (haystack.isEmpty()) return emptyList()

        val bySpeaker = transcript.segments
            .groupBy { it.speaker }
            .mapValues { (_, segs) -> normalize(segs.joinToString(" ") { it.text }) }

        return parsed.mapNotNull { item ->
            val needle = normalize(trimQuoteEdges(item.proofQuote))
            if (needle.isEmpty() || needle !in haystack) return@mapNotNull null

            val speaker = attribute(needle, bySpeaker)
            PromiseItem(
                task = item.task,
                quantity = item.quantity,
                proofQuote = item.proofQuote,
                attributedTo = speaker,
                confirmed = !item.markedUnconfirmed && speaker != Speaker.UNKNOWN,
            )
        }
    }

    /** The quote belongs to whoever's own words contain it — exactly one known speaker. */
    private fun attribute(needle: String, bySpeaker: Map<Speaker, String>): Speaker {
        val owners = bySpeaker.filter { (speaker, text) ->
            speaker != Speaker.UNKNOWN && needle in text
        }.keys
        return owners.singleOrNull() ?: Speaker.UNKNOWN
    }

    private val WHITESPACE = Regex("\\s+")

    private fun normalize(text: String): String =
        text.lowercase().replace(WHITESPACE, " ").trim()

    /** Strip punctuation the model commonly adds/drops at the edges of a quote. */
    private fun trimQuoteEdges(quote: String): String =
        quote.trim { it.isWhitespace() || it in EDGE_PUNCTUATION }

    private const val EDGE_PUNCTUATION = ".,;:!?\"'“”‘’…-"
}
