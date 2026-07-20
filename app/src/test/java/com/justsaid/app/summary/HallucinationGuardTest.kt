package com.justsaid.app.summary

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.core.Speaker
import com.justsaid.app.core.Transcript
import com.justsaid.app.core.TranscriptSegment
import org.junit.Test

/**
 * TESTING.md C.1 [CRITICAL]: the code-enforced side of Constitution G1. A quote
 * the model invented never becomes a [com.justsaid.app.core.PromiseItem]; matching
 * ignores case/whitespace; attribution comes from the transcript, and anything
 * unattributable is kept only as "Unconfirmed" (S2), never guessed.
 */
class HallucinationGuardTest {

    private fun transcript(vararg segments: Pair<Speaker, String>) = Transcript(
        segments.mapIndexed { i, (speaker, text) ->
            TranscriptSegment(speaker, text, startMs = i * 1000L, endMs = i * 1000L + 900L)
        },
    )

    private fun parsed(
        quote: String,
        task: String = "Some task",
        quantity: String? = null,
        markedUnconfirmed: Boolean = false,
    ) = ParsedPromise(task, quantity, quote, markedUnconfirmed)

    @Test
    fun `quote not in transcript is dropped`() {
        val t = transcript(Speaker.LOCAL to "I'll pick up the dry cleaning tomorrow")

        val items = HallucinationGuard.validate(
            listOf(parsed(quote = "I promise to wash the car")),
            t,
        )

        assertThat(items).isEmpty()
    }

    @Test
    fun `case and whitespace differences still match`() {
        val t = transcript(Speaker.LOCAL to "I'll  Pick Up   the DRY cleaning tomorrow")

        val items = HallucinationGuard.validate(
            listOf(parsed(quote = "i'll pick up the dry cleaning")),
            t,
        )

        assertThat(items).hasSize(1)
        assertThat(items.first().attributedTo).isEqualTo(Speaker.LOCAL)
        assertThat(items.first().confirmed).isTrue()
    }

    @Test
    fun `unknown speaker quote is kept but unconfirmed`() {
        val t = transcript(Speaker.UNKNOWN to "somebody will bring the cake on Friday")

        val items = HallucinationGuard.validate(
            listOf(parsed(quote = "will bring the cake on Friday")),
            t,
        )

        assertThat(items).hasSize(1)
        assertThat(items.first().attributedTo).isEqualTo(Speaker.UNKNOWN)
        assertThat(items.first().confirmed).isFalse()
    }

    @Test
    fun `quote appearing in both speakers cannot be attributed`() {
        val t = transcript(
            Speaker.LOCAL to "yes I'll call the doctor",
            Speaker.REMOTE to "so you said I'll call the doctor right",
        )

        val items = HallucinationGuard.validate(listOf(parsed(quote = "I'll call the doctor")), t)

        assertThat(items).hasSize(1)
        assertThat(items.first().attributedTo).isEqualTo(Speaker.UNKNOWN)
        assertThat(items.first().confirmed).isFalse()
    }

    @Test
    fun `remote speaker quote attributes to remote`() {
        val t = transcript(
            Speaker.LOCAL to "can you send the photos",
            Speaker.REMOTE to "sure, I'll send the photos tonight",
        )

        val items = HallucinationGuard.validate(listOf(parsed(quote = "I'll send the photos tonight")), t)

        assertThat(items).hasSize(1)
        assertThat(items.first().attributedTo).isEqualTo(Speaker.REMOTE)
        assertThat(items.first().confirmed).isTrue()
    }

    @Test
    fun `model-flagged unconfirmed stays unconfirmed even when attributable`() {
        val t = transcript(Speaker.LOCAL to "maybe I'll mow the lawn this weekend")

        val items = HallucinationGuard.validate(
            listOf(parsed(quote = "maybe I'll mow the lawn", markedUnconfirmed = true)),
            t,
        )

        assertThat(items).hasSize(1)
        assertThat(items.first().attributedTo).isEqualTo(Speaker.LOCAL)
        assertThat(items.first().confirmed).isFalse()
    }

    @Test
    fun `edge punctuation on the quote is tolerated`() {
        val t = transcript(Speaker.LOCAL to "I'll bring the ladder over on Saturday morning")

        val items = HallucinationGuard.validate(
            listOf(parsed(quote = "\"I'll bring the ladder over on Saturday morning.\"")),
            t,
        )

        assertThat(items).hasSize(1)
    }

    @Test
    fun `empty transcript drops everything`() {
        val items = HallucinationGuard.validate(
            listOf(parsed(quote = "anything at all")),
            Transcript(emptyList()),
        )

        assertThat(items).isEmpty()
    }

    @Test
    fun `mixed batch keeps only evidenced items`() {
        val t = transcript(
            Speaker.LOCAL to "I'll order the birthday cake",
            Speaker.REMOTE to "and I can pick up the balloons",
        )

        val items = HallucinationGuard.validate(
            listOf(
                parsed(task = "Order cake", quote = "I'll order the birthday cake"),
                parsed(task = "Hire a clown", quote = "I'll hire a clown"),
                parsed(task = "Pick up balloons", quote = "I can pick up the balloons"),
            ),
            t,
        )

        assertThat(items.map { it.task }).containsExactly("Order cake", "Pick up balloons").inOrder()
    }
}
