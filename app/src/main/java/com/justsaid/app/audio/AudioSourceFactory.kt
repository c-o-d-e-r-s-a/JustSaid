package com.justsaid.app.audio

import kotlinx.coroutines.CoroutineDispatcher

/** Chooses the best available capture source at runtime (Tier 1 -> 2 -> 3). */
fun interface AudioSourceFactory {
    fun create(): AudioSource
}

/**
 * Reports whether a given tier can actually capture on this device. Separated from the
 * factory so the (pure) selection order can be unit-tested with faked capabilities.
 */
fun interface TierProbe {
    fun isAvailable(tier: CaptureTier): Boolean
}

/**
 * Pure tier-selection policy: prefer true stereo, then dual-mono, and always fall back to
 * mic-only (which is universally available given RECORD_AUDIO). Never promises stereo.
 */
fun selectTier(probe: TierProbe): CaptureTier = when {
    probe.isAvailable(CaptureTier.STEREO) -> CaptureTier.STEREO
    probe.isAvailable(CaptureTier.DUAL_MONO) -> CaptureTier.DUAL_MONO
    else -> CaptureTier.MIC_ONLY
}

/**
 * Production factory: probes tiers via [probe], then hands back the matching source wired to
 * the capture [dispatcher].
 */
class RealAudioSourceFactory(
    private val probe: TierProbe,
    private val dispatcher: CoroutineDispatcher,
) : AudioSourceFactory {

    override fun create(): AudioSource = when (selectTier(probe)) {
        CaptureTier.STEREO -> VoiceCallAudioSource(dispatcher)
        CaptureTier.DUAL_MONO -> VoiceRecognitionAudioSource(dispatcher)
        CaptureTier.MIC_ONLY -> MicAudioSource(dispatcher)
    }
}
