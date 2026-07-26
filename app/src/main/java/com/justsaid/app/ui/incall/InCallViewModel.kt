package com.justsaid.app.ui.incall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justsaid.app.audio.CaptureController
import com.justsaid.app.audio.CaptureTier
import com.justsaid.app.audio.PipelineState
import com.justsaid.app.data.contacts.ContactResolver
import com.justsaid.app.telecom.CallActions
import com.justsaid.app.telecom.CallAudioGateway
import com.justsaid.app.telecom.CallState
import com.justsaid.app.telecom.CallStateHolder
import com.justsaid.app.telecom.phoneNumber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Coarse phase the in-call screen renders. */
enum class CallPhase { RINGING, ACTIVE, HELD, DISCONNECTED, NONE }

/** Immutable state for [InCallScreen]. UI is a pure function of this (MVVM). */
data class InCallUiState(
    val visible: Boolean = false,
    val phase: CallPhase = CallPhase.NONE,
    val displayName: String = "",
    val listenEnabled: Boolean = false,
    val isCapturing: Boolean = false,
    val showLoadingModal: Boolean = false,
    val speakerOn: Boolean = false,
    val captureTier: CaptureTier? = null,
    val showMicOnlySpeakerHint: Boolean = false,
)

/**
 * Surfaces call + capture state to the in-call overlay and routes user actions
 * (toggle LISTEN, answer, hang up) back through the capture controller / telecom wrappers.
 */
@HiltViewModel
class InCallViewModel @Inject constructor(
    private val callStateHolder: CallStateHolder,
    private val captureController: CaptureController,
    private val callActions: CallActions,
    private val callAudioGateway: CallAudioGateway,
    private val contactResolver: ContactResolver,
) : ViewModel() {

    private val displayName = MutableStateFlow("")
    private val micOnlyHintDismissed = MutableStateFlow(false)

    init {
        // Resolve the remote number -> contact name whenever the number changes.
        viewModelScope.launch {
            callStateHolder.state
                .map { it.phoneNumber }
                .distinctUntilChanged()
                .collect { number ->
                    displayName.value = when {
                        number.isBlank() -> ""
                        else -> contactResolver.resolve(number) ?: number
                    }
                }
        }
        // Auto-dismiss the overlay once the controller signals the post-call step is done
        // (DONE is reached whether or not audio was captured), returning to Idle.
        viewModelScope.launch {
            captureController.pipelineState.collect { pipeline ->
                if (pipeline == PipelineState.DONE) {
                    captureController.onPostCallDismissed()
                }
            }
        }
    }

    val state: StateFlow<InCallUiState> = combine(
        combine(
            callStateHolder.state,
            captureController.listenEnabled,
            captureController.isCapturing,
            captureController.pipelineState,
        ) { call, listen, capturing, pipeline ->
            CallCapturePhase(call, listen, capturing, pipeline)
        },
        captureController.activeCaptureTier,
        callAudioGateway.speakerOn,
        displayName,
        micOnlyHintDismissed,
    ) { phase, tier, speakerOn, name, hintDismissed ->
        val showHint = phase.listen &&
            tier == CaptureTier.MIC_ONLY &&
            !speakerOn &&
            !hintDismissed &&
            phase.call is CallState.Active
        InCallUiState(
            visible = phase.call !is CallState.Idle,
            phase = phase.call.toPhase(),
            displayName = name.ifBlank { phase.call.phoneNumber },
            listenEnabled = phase.listen,
            isCapturing = phase.capturing,
            showLoadingModal = phase.pipeline == PipelineState.PROCESSING,
            speakerOn = speakerOn,
            captureTier = tier,
            showMicOnlySpeakerHint = showHint,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InCallUiState())

    private data class CallCapturePhase(
        val call: CallState,
        val listen: Boolean,
        val capturing: Boolean,
        val pipeline: PipelineState,
    )

    fun onToggleListen(enabled: Boolean) = captureController.setListen(enabled)
    fun onAnswer() = callActions.answer()
    fun onHangup() = callActions.hangup()
    fun onToggleSpeaker() = callAudioGateway.toggleSpeaker()
    fun onDismissMicOnlyHint() {
        micOnlyHintDismissed.value = true
    }

    private fun CallState.toPhase(): CallPhase = when (this) {
        is CallState.Idle -> CallPhase.NONE
        is CallState.Ringing -> CallPhase.RINGING
        is CallState.Active -> CallPhase.ACTIVE
        is CallState.Held -> CallPhase.HELD
        is CallState.Disconnected -> CallPhase.DISCONNECTED
    }
}
