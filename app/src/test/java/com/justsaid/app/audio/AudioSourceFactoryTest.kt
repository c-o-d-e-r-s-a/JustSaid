package com.justsaid.app.audio

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import org.junit.Test

class AudioSourceFactoryTest {

    @Test
    fun `factory always returns mic-only source`() {
        val factory = RealAudioSourceFactory(Dispatchers.Unconfined)
        val source = factory.create()
        assertThat(source.tier).isEqualTo(CaptureTier.MIC_ONLY)
        assertThat(source).isInstanceOf(MicrophoneAudioSource::class.java)
        assertThat(source.channels).isEqualTo(1)
    }
}
