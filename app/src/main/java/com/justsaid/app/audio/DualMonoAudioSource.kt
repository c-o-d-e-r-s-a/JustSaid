package com.justsaid.app.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Tier 2 — dual-mono via a call-oriented `AudioRecord` source (typically
 * [android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION] or
 * [android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION]). Both parties
 * are mixed to mono; speakers stay UNKNOWN downstream (Constitution S2).
 */
class DualMonoAudioSource(
    private val audioSource: Int,
    private val dispatcher: CoroutineDispatcher,
) : AudioSource {
    override val tier: CaptureTier = CaptureTier.DUAL_MONO
    override val channels: Int = 1
    override fun frames(): Flow<ShortArray> =
        audioRecordFrames(audioSource, MONO, dispatcher)
}
