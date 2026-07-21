package com.justsaid.app.summary

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.PromiseItem
import com.justsaid.app.core.Speaker
import org.junit.Test

/**
 * Phase 5 doc: item rendering, Unconfirmed labeling, quantity omission. This is the
 * single source of truth for the SMS body and the exports, so the format is pinned here.
 */
class SummaryMarkdownTest {

    private val unconfirmedLabel = "Unconfirmed"

    private fun item(
        task: String = "Buy milk",
        quantity: String? = null,
        proof: String = "I'll buy milk tomorrow",
        confirmed: Boolean = true,
    ) = PromiseItem(
        task = task,
        quantity = quantity,
        proofQuote = proof,
        attributedTo = if (confirmed) Speaker.LOCAL else Speaker.UNKNOWN,
        confirmed = confirmed,
    )

    private fun summary(items: List<PromiseItem>, contactName: String? = "Ada") = CallSummary(
        id = 1L,
        contactName = contactName,
        phoneNumber = "+15555550123",
        createdAt = 1_752_986_000_000L,
        items = items,
        fullTranscript = "You: I'll buy milk tomorrow",
    )

    @Test
    fun `header uses contact name and falls back to number`() {
        val named = SummaryMarkdown.render(summary(emptyList()), unconfirmedLabel)
        assertThat(named).startsWith("Ada — ")

        val unnamed = SummaryMarkdown.render(summary(emptyList(), contactName = null), unconfirmedLabel)
        assertThat(unnamed).startsWith("+15555550123 — ")
    }

    @Test
    fun `item renders bullet, task and quoted proof`() {
        val rendered = SummaryMarkdown.render(summary(listOf(item())), unconfirmedLabel)

        assertThat(rendered).contains("• Buy milk")
        assertThat(rendered).contains("\"I'll buy milk tomorrow\"")
    }

    @Test
    fun `quantity shown in brackets only when present`() {
        val withQty = SummaryMarkdown.renderItem(item(quantity = "2 liters"), unconfirmedLabel)
        assertThat(withQty).contains("Buy milk [2 liters]")

        val withoutQty = SummaryMarkdown.renderItem(item(quantity = null), unconfirmedLabel)
        assertThat(withoutQty).doesNotContain("[")
        assertThat(withoutQty).doesNotContain("]")
    }

    @Test
    fun `unconfirmed items are labeled, confirmed ones are not`() {
        val unconfirmed = SummaryMarkdown.renderItem(item(confirmed = false), unconfirmedLabel)
        assertThat(unconfirmed).contains("Unconfirmed: Buy milk")

        val confirmed = SummaryMarkdown.renderItem(item(confirmed = true), unconfirmedLabel)
        assertThat(confirmed).doesNotContain("Unconfirmed")
    }

    @Test
    fun `multiple items each get their own bullet`() {
        val rendered = SummaryMarkdown.render(
            summary(listOf(item(task = "Buy milk"), item(task = "Call the doctor", proof = "I will call the doctor"))),
            unconfirmedLabel,
        )

        assertThat(rendered).contains("• Buy milk")
        assertThat(rendered).contains("• Call the doctor")
    }

    @Test
    fun `summary with no items is just the header`() {
        val rendered = SummaryMarkdown.render(summary(emptyList()), unconfirmedLabel)
        assertThat(rendered).doesNotContain("•")
        assertThat(rendered.lines()).hasSize(1)
    }
}
