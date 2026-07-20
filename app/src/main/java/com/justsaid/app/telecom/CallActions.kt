package com.justsaid.app.telecom

import android.telecom.Call
import android.telecom.VideoProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over the active `android.telecom.Call` so the UI never touches the
 * platform Call object directly. The current Call is set/cleared by
 * [JustSaidInCallService]; UI just calls [answer]/[hangup].
 */
@Singleton
class CallActions @Inject constructor() {

    @Volatile
    private var current: Call? = null

    fun bind(call: Call?) {
        current = call
    }

    fun answer() {
        current?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun hangup() {
        val call = current ?: return
        // Reject a still-ringing call; disconnect anything already connected.
        if (call.state == Call.STATE_RINGING) {
            call.reject(false, null)
        } else {
            call.disconnect()
        }
    }
}
