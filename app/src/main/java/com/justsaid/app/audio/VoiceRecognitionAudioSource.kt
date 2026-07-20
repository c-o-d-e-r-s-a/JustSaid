package com.justsaid.app.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Tier 2 — dual-mono via `VOICE_RECOGNITION`. On many OEMs this captures both parties
 * (the "Cube ACR" technique, often needing an enabled AccessibilityService) but with no
 * channel separation, so speakers stay UNKNOWN downstream (Constitution S2).
 */
class VoiceRecognitionAudioSource(
    private val dispatcher: CoroutineDispatcher,
) : AudioSource {
    override val tier: CaptureTier = CaptureTier.DUAL_MONO
    override val channels: Int = 1
    override fun frames(): Flow<ShortArray> =
        audioRecordFrames(Src.VOICE_RECOGNITION, MONO, dispatcher)
}
