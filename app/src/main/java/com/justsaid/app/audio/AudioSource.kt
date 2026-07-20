package com.justsaid.app.audio

import kotlinx.coroutines.flow.Flow

/**
 * A live PCM capture source. Implementations wrap a specific `AudioRecord`
 * configuration (see the tiered strategy in `docs/02`). All sources emit 16-bit
 * signed PCM at [SAMPLE_RATE]; the frame layout depends on [channels]:
 *   - mono  (channels == 1): consecutive samples.
 *   - stereo(channels == 2): interleaved L,R,L,R (Tier 1 only).
 *
 * Capture runs until the collecting coroutine is cancelled; there is no separate
 * stop() — cancelling the [frames] Flow releases the underlying recorder.
 */
interface AudioSource {

    val tier: CaptureTier

    /** 2 only for the true-stereo tier; 1 otherwise. */
    val channels: Int

    /**
     * Cold flow of PCM frames. Collecting starts the recorder; cancellation stops and
     * releases it. Emits nothing (and does not throw) if the source cannot start.
     */
    fun frames(): Flow<ShortArray>

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
