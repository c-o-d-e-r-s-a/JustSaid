package com.justsaid.app.session

/** Explicit manual-capture lifecycle exposed to the UI via [CaptureSessionController]. */
sealed interface CaptureSessionState {
    data object Idle : CaptureSessionState
    data class Recording(val sessionId: String, val startedAt: Long) : CaptureSessionState
    data class Finalizing(val sessionId: String) : CaptureSessionState
    data class Processing(val sessionId: String) : CaptureSessionState
    data class Completed(val sessionId: String) : CaptureSessionState
    data class Failed(val userMessage: String) : CaptureSessionState
}
