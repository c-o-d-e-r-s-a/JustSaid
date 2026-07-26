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

/** Picks which `AudioRecord` source to use for Tier 2 after [TierProbe] approves dual-mono. */
fun interface DualMonoSourceSelector {
    /** First source with live signal, or null if none (factory falls back to VR). */
    fun select(): Int?
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
    private val dualMonoSelector: DualMonoSourceSelector,
    private val dispatcher: CoroutineDispatcher,
) : AudioSourceFactory {

    constructor(
        recordProbe: AudioRecordTierProbe,
        dispatcher: CoroutineDispatcher,
    ) : this(recordProbe, recordProbe, dispatcher)

    override fun create(): AudioSource = when (selectTier(probe)) {
        CaptureTier.STEREO -> VoiceCallAudioSource(dispatcher)
        CaptureTier.DUAL_MONO -> {
            val source = dualMonoSelector.select() ?: Src.VOICE_RECOGNITION
            DualMonoAudioSource(source, dispatcher)
        }
        CaptureTier.MIC_ONLY -> MicAudioSource(dispatcher)
    }
}
