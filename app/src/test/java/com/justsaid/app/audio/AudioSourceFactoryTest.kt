package com.justsaid.app.audio

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import org.junit.Test

class AudioSourceFactoryTest {

    private fun probeOf(vararg available: CaptureTier): TierProbe =
        TierProbe { it in available.toSet() }

    @Test
    fun `prefers stereo when available`() {
        val tier = selectTier(probeOf(CaptureTier.STEREO, CaptureTier.DUAL_MONO, CaptureTier.MIC_ONLY))
        assertThat(tier).isEqualTo(CaptureTier.STEREO)
    }

    @Test
    fun `falls back to dual-mono when stereo unavailable`() {
        val tier = selectTier(probeOf(CaptureTier.DUAL_MONO, CaptureTier.MIC_ONLY))
        assertThat(tier).isEqualTo(CaptureTier.DUAL_MONO)
    }

    @Test
    fun `falls back to mic-only when nothing else available`() {
        val tier = selectTier(probeOf()) // probe says nothing is available
        assertThat(tier).isEqualTo(CaptureTier.MIC_ONLY)
    }

    @Test
    fun `factory produces a source matching the selected tier`() {
        val factory = RealAudioSourceFactory(
            probe = probeOf(CaptureTier.DUAL_MONO, CaptureTier.MIC_ONLY),
            dispatcher = Dispatchers.Unconfined,
        )
        val source = factory.create()
        assertThat(source.tier).isEqualTo(CaptureTier.DUAL_MONO)
        assertThat(source).isInstanceOf(VoiceRecognitionAudioSource::class.java)
        assertThat(source.channels).isEqualTo(1)
    }

    @Test
    fun `stereo tier reports two channels`() {
        val factory = RealAudioSourceFactory(
            probe = TierProbe { true },
            dispatcher = Dispatchers.Unconfined,
        )
        val source = factory.create()
        assertThat(source.tier).isEqualTo(CaptureTier.STEREO)
        assertThat(source.channels).isEqualTo(2)
    }
}
