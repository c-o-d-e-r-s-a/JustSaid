package com.justsaid.app.audio

import android.media.MediaRecorder
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import org.junit.Test

class MicrophoneAudioSourceTest {

  @Test
  fun `production source is MIC mono`() {
    val source = MicrophoneAudioSource(Dispatchers.Unconfined)
    assertThat(source.tier).isEqualTo(CaptureTier.MIC_ONLY)
    assertThat(source.channels).isEqualTo(1)
    assertThat(source).isInstanceOf(MicrophoneAudioSource::class.java)
    assertThat(MediaRecorder.AudioSource.MIC).isEqualTo(MediaRecorder.AudioSource.MIC)
  }
}
