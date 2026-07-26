package com.justsaid.app.telecom

import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes in-call audio (speaker vs earpiece) through the bound [InCallService].
 * The service attaches on bind and forwards [InCallService.onCallAudioStateChanged].
 */
@Singleton
class CallAudioGateway @Inject constructor() {

    @Volatile
    private var inCallService: InCallService? = null

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    fun attach(service: InCallService) {
        inCallService = service
        service.callAudioState?.let { onCallAudioStateChanged(it) }
    }

    fun detach() {
        inCallService = null
        _speakerOn.value = false
    }

    fun onCallAudioStateChanged(state: CallAudioState) {
        _speakerOn.value = state.route == CallAudioState.ROUTE_SPEAKER
    }

    /** Toggles between speakerphone and earpiece for the active carrier call. */
    fun setSpeakerEnabled(enabled: Boolean) {
        val service = inCallService ?: return
        val route = if (enabled) {
            CallAudioState.ROUTE_SPEAKER
        } else {
            CallAudioState.ROUTE_EARPIECE
        }
        service.setAudioRoute(route)
    }

    fun toggleSpeaker() {
        setSpeakerEnabled(!_speakerOn.value)
    }
}
