package com.justsaid.app.core

/**
 * Who said a segment. STEREO capture maps L->LOCAL, R->REMOTE (Constitution S1);
 * mono capture cannot attribute, so everything is UNKNOWN (S2).
 */
enum class Speaker { LOCAL, REMOTE, UNKNOWN }

/** One contiguous utterance produced by the STT engine, timed against the call start. */
data class TranscriptSegment(
    val speaker: Speaker,
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/**
 * The full call transcript produced by Phase 3 (STT) and consumed by Phase 4 (LLM).
 * Segments are ordered by [TranscriptSegment.startMs].
 */
data class Transcript(val segments: List<TranscriptSegment>) {

    /**
     * Plain-text rendering fed to the LLM and used for the verbatim-proof substring
     * check (Constitution G1). UNKNOWN speakers render as `SPEAKER_?` per S2 so the
     * summarizer never guesses an attribution.
     */
    fun plainText(): String = segments.joinToString("\n") { seg ->
        val label = when (seg.speaker) {
            Speaker.LOCAL -> "You"
            Speaker.REMOTE -> "Them"
            Speaker.UNKNOWN -> "SPEAKER_?"
        }
        "$label: ${seg.text.trim()}"
    }
}
