package com.justsaid.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Verifies the mic-gating policy: the `microphone` FGS type is added ONLY when capture is active
 * AND RECORD_AUDIO is granted. Uses arbitrary flag values so the bitwise logic is asserted
 * independently of the real platform constants.
 */
class ForegroundServiceTypeTest {

    private val phoneCall = 0b0100 // 4
    private val microphone = 0b1000 // 8

    @Test
    fun `phoneCall only when not capturing`() {
        val type = foregroundServiceType(
            micRequested = false, hasRecordAudio = true,
            phoneCallType = phoneCall, microphoneType = microphone,
        )
        assertThat(type).isEqualTo(phoneCall)
    }

    @Test
    fun `phoneCall only when capturing but permission missing`() {
        val type = foregroundServiceType(
            micRequested = true, hasRecordAudio = false,
            phoneCallType = phoneCall, microphoneType = microphone,
        )
        assertThat(type).isEqualTo(phoneCall)
    }

    @Test
    fun `adds microphone only when capturing and permission granted`() {
        val type = foregroundServiceType(
            micRequested = true, hasRecordAudio = true,
            phoneCallType = phoneCall, microphoneType = microphone,
        )
        assertThat(type).isEqualTo(phoneCall or microphone)
    }
}
