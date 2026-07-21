package com.justsaid.app.ui.summary

import androidx.lifecycle.ViewModel
import com.justsaid.app.core.CallSummary
import com.justsaid.app.summary.SummaryEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * NavHost-level gate: when the pipeline publishes a summary, [pending] turns
 * non-null and the host navigates to the summary route (the Phase 2 loading
 * modal has just dismissed at that point).
 */
@HiltViewModel
class SummaryGateViewModel @Inject constructor(
    summaryEvents: SummaryEvents,
) : ViewModel() {

    val pending: StateFlow<CallSummary?> = summaryEvents.latest
}
