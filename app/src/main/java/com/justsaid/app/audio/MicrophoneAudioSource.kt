package com.justsaid.app.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Sole production capture source: [MediaRecorder.AudioSource.MIC], mono 16 kHz.
 * Remote voices are incidental (speakerphone); never attributed downstream.
 */
class MicrophoneAudioSource(
    private val dispatcher: CoroutineDispatcher,
) : AudioSource {
    override val tier: CaptureTier = CaptureTier.MIC_ONLY
    override val channels: Int = 1
    override fun frames(): Flow<ShortArray> =
        audioRecordFrames(Src.MIC, MONO, dispatcher)
}
