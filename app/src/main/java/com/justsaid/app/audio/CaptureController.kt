package com.justsaid.app.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Post-call pipeline phase, drives the full-screen loading modal in the UI. */
enum class PipelineState { IDLE, PROCESSING, DONE }

/**
 * Legacy capture coordinator stub. Call-lifecycle integration was removed in migration
 * task 1; [com.justsaid.app.session.CaptureSessionController] replaces this in task 2.
 */
@Singleton
class CaptureController @Inject constructor() {

    private val _listenEnabled = MutableStateFlow(false)
    val listenEnabled: StateFlow<Boolean> = _listenEnabled.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _activeCaptureTier = MutableStateFlow<CaptureTier?>(null)
    val activeCaptureTier: StateFlow<CaptureTier?> = _activeCaptureTier.asStateFlow()

    private val _pipelineState = MutableStateFlow(PipelineState.IDLE)
    val pipelineState: StateFlow<PipelineState> = _pipelineState.asStateFlow()

    fun setListen(enabled: Boolean) {
        _listenEnabled.value = enabled
    }

    fun onPostCallDismissed() {
        _pipelineState.value = PipelineState.IDLE
    }
}
