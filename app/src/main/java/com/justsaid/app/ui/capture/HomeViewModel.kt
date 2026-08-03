package com.justsaid.app.ui.capture

import androidx.lifecycle.ViewModel
import com.justsaid.app.session.CaptureSessionController
import com.justsaid.app.session.CaptureSessionState
import com.justsaid.app.summary.SummaryEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Manual capture home: wires [CaptureSessionController] start/stop to the UI and
 * surfaces the pipeline's unsaved summary via [SummaryEvents].
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val captureController: CaptureSessionController,
    private val summaryEvents: SummaryEvents,
) : ViewModel() {

    val captureState: StateFlow<CaptureSessionState> = captureController.state
    val pendingSummary: StateFlow<com.justsaid.app.core.CallSummary?> = summaryEvents.latest

    fun startListening(sessionLabel: String?) {
        summaryEvents.clear()
        captureController.start(sessionLabel?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun stopListening() {
        captureController.stop()
    }

    fun dismissError() {
        captureController.reset()
    }

    fun dismissSummary() {
        summaryEvents.clear()
        if (captureController.state.value is CaptureSessionState.Completed) {
            captureController.reset()
        }
    }
}
