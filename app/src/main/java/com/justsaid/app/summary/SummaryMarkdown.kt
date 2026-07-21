package com.justsaid.app.summary

import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.PromiseItem
import java.text.DateFormat
import java.util.Date

/**
 * The single place a [CallSummary] becomes shareable text (Phase 5 doc: reused
 * by the SMS body, the text export, and the PDF lines). Pure JVM so it unit-tests
 * without a device; user-visible labels are passed in from `strings.xml`.
 */
object SummaryMarkdown {

    /**
     * Renders the summary as simple markdown: a header line with who + when,
     * then one bullet per promise with its verbatim proof quote underneath.
     * [unconfirmedLabel] prefixes items whose speaker could not be confirmed (S2).
     */
    fun render(summary: CallSummary, unconfirmedLabel: String): String = buildString {
        append(summary.contactName ?: summary.phoneNumber)
        append(" — ")
        append(formatDate(summary.createdAt))
        for (item in summary.items) {
            append("\n\n")
            append(renderItem(item, unconfirmedLabel))
        }
    }

    /** Renders one item: `• task [qty]` then the quote on its own indented line. */
    fun renderItem(item: PromiseItem, unconfirmedLabel: String): String = buildString {
        append("• ")
        if (!item.confirmed) {
            append(unconfirmedLabel)
            append(": ")
        }
        append(item.task)
        item.quantity?.let { append(" [").append(it).append("]") }
        append("\n  \"")
        append(item.proofQuote)
        append("\"")
    }

    /** Locale-aware "Jul 20, 2026, 12:38 AM" style stamp for headers and lists. */
    fun formatDate(epochMillis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(epochMillis))
}
