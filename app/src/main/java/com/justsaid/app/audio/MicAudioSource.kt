package com.justsaid.app.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Tier 3 — universal fallback via `MIC`. Captures the local user clearly; the remote party
 * only if the call is on speakerphone. Always available given RECORD_AUDIO, so it is the
 * guaranteed last resort. Mono, speakers UNKNOWN.
 */
class MicAudioSource(
    private val dispatcher: CoroutineDispatcher,
) : AudioSource {
    override val tier: CaptureTier = CaptureTier.MIC_ONLY
    override val channels: Int = 1
    override fun frames(): Flow<ShortArray> =
        audioRecordFrames(Src.MIC, MONO, dispatcher)
}
