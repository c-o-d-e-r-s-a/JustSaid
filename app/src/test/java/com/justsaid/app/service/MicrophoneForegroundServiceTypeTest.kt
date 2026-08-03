package com.justsaid.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Microphone-only FGS policy for the companion capture service. */
class MicrophoneForegroundServiceTypeTest {

  private val microphone = 0b1000

  @Test
  fun `returns zero when record audio not granted`() {
    assertThat(microphoneForegroundServiceType(hasRecordAudio = false, microphoneType = microphone))
      .isEqualTo(0)
  }

  @Test
  fun `returns microphone type when permission granted`() {
    assertThat(microphoneForegroundServiceType(hasRecordAudio = true, microphoneType = microphone))
      .isEqualTo(microphone)
  }
}
