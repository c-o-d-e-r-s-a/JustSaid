package com.justsaid.app.summary

import com.justsaid.app.core.CallSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the freshly produced [CallSummary] from the pipeline to whoever renders it
 * (Phase 5's summary screen observes [latest] and calls [clear] once consumed).
 * State, not a one-shot event, so a summary produced while the UI is backgrounded
 * is still there when the screen resumes.
 */
@Singleton
class SummaryEvents @Inject constructor() : PendingSummaryHandoff {

    private val _latest = MutableStateFlow<CallSummary?>(null)
    val latest: StateFlow<CallSummary?> = _latest.asStateFlow()

    override fun publish(summary: CallSummary) {
        _latest.value = summary
    }

    fun clear() {
        _latest.value = null
    }
}
