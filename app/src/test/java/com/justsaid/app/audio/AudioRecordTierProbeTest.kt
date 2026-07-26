package com.justsaid.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM tests for the probe's silence detection ([probeHasLiveSignal]) — the guard
 * against OEM-muted streams that start fine but deliver only zeros (Samsung One UI
 * in-call behavior). The `AudioRecord` plumbing itself is device-only.
 */
class AudioRecordTierProbeTest {

    /** Simulates a recorder that fills each read fully with the given sample producer. */
    private fun readerOf(sampleAt: (index: Int) -> Short): (ShortArray) -> Int {
        var position = 0
        return { buffer ->
            for (i in buffer.indices) {
                buffer[i] = sampleAt(position + i)
            }
            position += buffer.size
            buffer.size
        }
    }

    @Test
    fun `all-zero stream is not a live signal`() {
        assertThat(probeHasLiveSignal(read = readerOf { 0 })).isFalse()
    }

    @Test
    fun `noise floor counts as live signal`() {
        assertThat(probeHasLiveSignal(read = readerOf { 1 })).isTrue()
    }

    @Test
    fun `signal appearing late in the window is still detected`() {
        val lastIndex = AudioSource.SAMPLE_RATE * PROBE_WINDOW_MS / 1000 - 1
        val reader = readerOf { index -> if (index == lastIndex) 42 else 0 }
        assertThat(probeHasLiveSignal(read = reader)).isTrue()
    }

    @Test
    fun `dead stream (non-positive read) is not a live signal`() {
        assertThat(probeHasLiveSignal(read = { -1 })).isFalse()
        assertThat(probeHasLiveSignal(read = { 0 })).isFalse()
    }

    @Test
    fun `partial reads accumulate toward the probe window`() {
        var calls = 0
        val reader: (ShortArray) -> Int = { buffer ->
            calls++
            buffer[0] = 0
            1 // one zero sample per read; must keep reading, not loop forever
        }
        assertThat(probeHasLiveSignal(samplesToRead = 8, read = reader)).isFalse()
        assertThat(calls).isEqualTo(8)
    }

    @Test
    fun `dual mono selection tries recognition before communication`() {
        val tried = mutableListOf<Int>()
        val chosen = selectDualMonoSource { source ->
            tried += source
            source == Src.VOICE_COMMUNICATION
        }
        assertThat(chosen).isEqualTo(Src.VOICE_COMMUNICATION)
        assertThat(tried).containsExactly(Src.VOICE_RECOGNITION, Src.VOICE_COMMUNICATION).inOrder()
    }
}
