package com.justsaid.app.summary

import com.justsaid.app.core.Speaker
import com.justsaid.app.core.Transcript
import com.justsaid.app.core.TranscriptSegment

/**
 * Splits a [Transcript] into bounded chunks so each fits the summarizer prompt
 * budget. Chunks break on segment boundaries when possible; oversized segments
 * are split on word boundaries (ponytail: naive word split, upgrade to sentence
 * boundaries if needed).
 */
object TranscriptChunker {

    /**
     * Returns one or more transcript views, each whose [Transcript.plainText] length
     * is at most [maxPlainTextChars].
     */
    fun chunks(transcript: Transcript, maxPlainTextChars: Int): List<Transcript> {
        require(maxPlainTextChars > 0) { "chunk budget must be positive" }
        if (transcript.segments.isEmpty()) return emptyList()

        val expanded = transcript.segments.flatMap { splitOversizedSegment(it, maxPlainTextChars) }
        if (expanded.isEmpty()) return emptyList()

        val single = Transcript(expanded, transcript.detectedLanguage)
        if (single.plainText().length <= maxPlainTextChars) return listOf(single)

        val out = mutableListOf<Transcript>()
        var current = mutableListOf<TranscriptSegment>()
        for (seg in expanded) {
            val candidate = current + seg
            val candidateText = Transcript(candidate, transcript.detectedLanguage).plainText()
            if (candidateText.length > maxPlainTextChars && current.isNotEmpty()) {
                out += Transcript(current.toList(), transcript.detectedLanguage)
                current = mutableListOf(seg)
            } else {
                current += seg
            }
        }
        if (current.isNotEmpty()) {
            out += Transcript(current.toList(), transcript.detectedLanguage)
        }
        return out
    }

    private fun splitOversizedSegment(
        segment: TranscriptSegment,
        maxPlainTextChars: Int,
    ): List<TranscriptSegment> {
        val overhead = speakerLabel(segment.speaker).length + 2 // "Label: "
        val maxTextChars = (maxPlainTextChars - overhead).coerceAtLeast(64)
        val text = segment.text.trim()
        if (text.length <= maxTextChars) return listOf(segment)

        val words = text.split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf(segment)

        val parts = mutableListOf<TranscriptSegment>()
        var bucket = StringBuilder()
        for (word in words) {
            val next = if (bucket.isEmpty()) word else "$bucket $word"
            if (next.length > maxTextChars && bucket.isNotEmpty()) {
                parts += segment.copy(text = bucket.toString())
                bucket = StringBuilder(word)
            } else {
                bucket = StringBuilder(next)
            }
        }
        if (bucket.isNotEmpty()) {
            parts += segment.copy(text = bucket.toString())
        }
        return parts.ifEmpty { listOf(segment) }
    }

    private fun speakerLabel(speaker: Speaker): String = when (speaker) {
        Speaker.LOCAL -> "You"
        Speaker.REMOTE -> "Them"
        Speaker.UNKNOWN -> "SPEAKER_?"
    }

    private val WHITESPACE = Regex("\\s+")
}
