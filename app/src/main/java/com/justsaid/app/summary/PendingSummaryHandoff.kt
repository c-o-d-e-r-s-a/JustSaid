package com.justsaid.app.summary

import com.justsaid.app.core.CallSummary

/**
 * Delivers an unsaved summary (id == 0) from the session pipeline to the summary
 * screen. Persistence is a separate explicit user action via [com.justsaid.app.data.repo.SummaryRepo].
 */
interface PendingSummaryHandoff {
    fun publish(summary: CallSummary)
}
