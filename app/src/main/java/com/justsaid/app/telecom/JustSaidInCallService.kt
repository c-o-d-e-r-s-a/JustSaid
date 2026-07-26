package com.justsaid.app.telecom

import android.telecom.Call
import android.telecom.InCallService
import com.justsaid.app.audio.CaptureController
import com.justsaid.app.service.CaptureForegroundService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The system dialer entry point. The telecom framework binds this once the app holds
 * `ROLE_DIALER`, delivering call lifecycle callbacks. This class ONLY translates those
 * callbacks into [CallStateHolder] updates and manages the capture foreground service —
 * all capture/UI logic lives elsewhere (AGENTS.md §4, one-directional state).
 */
@AndroidEntryPoint
class JustSaidInCallService : InCallService() {

    @Inject lateinit var callStateHolder: CallStateHolder
    @Inject lateinit var callActions: CallActions
    @Inject lateinit var callAudioGateway: CallAudioGateway

    // Injected so the singleton capture state machine is alive and observing call state as
    // soon as the framework binds us, even if the UI never opens.
    @Inject lateinit var captureController: CaptureController

    private val callbacks = mutableMapOf<Call, Call.Callback>()

    override fun onCreate() {
        super.onCreate()
        callAudioGateway.attach(this)
    }

    override fun onDestroy() {
        callAudioGateway.detach()
        super.onDestroy()
    }

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState?) {
        if (audioState != null) {
            callAudioGateway.onCallAudioStateChanged(audioState)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        callActions.bind(call)

        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                publish(c, state)
            }
        }
        callbacks[call] = callback
        call.registerCallback(callback)

        // The framework may add an already-active call (e.g. adb add-call / restart).
        publish(call, call.state)
        CaptureForegroundService.start(this)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        callbacks.remove(call)?.let(call::unregisterCallback)

        // Emit Disconnected (not Idle) so the capture controller reliably observes the
        // terminal transition; the UI resets to Idle once the post-call modal dismisses.
        callStateHolder.update(CallState.Disconnected(numberOf(call)))
        callActions.bind(null)
        CaptureForegroundService.stop(this)
    }

    private fun publish(call: Call, state: Int) {
        val number = numberOf(call)
        val mapped = when (state) {
            Call.STATE_RINGING -> CallState.Ringing(number)
            Call.STATE_DIALING, Call.STATE_CONNECTING -> CallState.Ringing(number)
            Call.STATE_ACTIVE -> CallState.Active(number)
            Call.STATE_HOLDING -> CallState.Held(number)
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallState.Disconnected(number)
            else -> return
        }
        callStateHolder.update(mapped)
    }

    private fun numberOf(call: Call): String =
        call.details?.handle?.schemeSpecificPart.orEmpty()
}
