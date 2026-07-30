package com.justsaid.app.summary

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.core.Speaker
import com.justsaid.app.core.Transcript
import com.justsaid.app.core.TranscriptSegment
import org.junit.Test

class CommitmentFallbackTest {

    @Test
    fun `fallback skipped when transcript language is not English`() {
        val t = transcript("मैं कल दूध खरीदूंगा")
        assertThat(CommitmentFallback.appliesTo(t.copy(detectedLanguage = "hi"))).isFalse()
        assertThat(CommitmentFallback.propose(t.copy(detectedLanguage = "hi"))).isEmpty()
    }

    private fun transcript(vararg lines: String) = Transcript(
        lines.mapIndexed { i, text ->
            TranscriptSegment(Speaker.UNKNOWN, text, i * 1000L, i * 1000L + 500L)
        },
    )

    @Test
    fun `splits two I-will commitments joined by and — A14 regression`() {
        val t = transcript("I will buy 2 liters of milk and I will fix the sink this weekend")
        val proposed = CommitmentFallback.propose(t)
        val kept = HallucinationGuard.validate(proposed, t)

        assertThat(kept).hasSize(2)
        assertThat(kept.map { it.proofQuote }).containsExactly(
            "I will buy 2 liters of milk",
            "I will fix the sink this weekend",
        ).inOrder()
        assertThat(kept.all { !it.confirmed }).isTrue() // UNKNOWN speaker → Unconfirmed
    }

    @Test
    fun `returns empty when transcript has no commitments`() {
        val t = transcript("Hello how are you today the weather is nice")
        assertThat(CommitmentFallback.propose(t)).isEmpty()
    }

    @Test
    fun `handles I'll contraction`() {
        val t = transcript("I'll call the plumber tomorrow")
        val kept = HallucinationGuard.validate(CommitmentFallback.propose(t), t)
        assertThat(kept).hasSize(1)
        assertThat(kept.first().proofQuote).isEqualTo("I'll call the plumber tomorrow")
    }
}
