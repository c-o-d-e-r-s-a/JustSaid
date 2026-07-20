package com.justsaid.app.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Tier 1 — true stereo (L=local, R=remote) via `VOICE_CALL`. Requires the privileged
 * `CAPTURE_AUDIO_OUTPUT` permission, which a sideloaded APK cannot obtain on Android 10+;
 * being the default dialer does NOT grant it. So this almost always fails to initialize
 * and the factory falls through to a lower tier — that is expected, not an error.
 */
class VoiceCallAudioSource(
    private val dispatcher: CoroutineDispatcher,
) : AudioSource {
    override val tier: CaptureTier = CaptureTier.STEREO
    override val channels: Int = 2
    override fun frames(): Flow<ShortArray> =
        audioRecordFrames(Src.VOICE_CALL, STEREO, dispatcher)
}
