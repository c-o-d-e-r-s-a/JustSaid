package com.justsaid.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord

/**
 * Real capability probe: tries to construct (and, for the privileged tier, briefly start)
 * an `AudioRecord` for the tier's source/channel config. If construction or start fails
 * the tier is unavailable and the factory falls through. MIC is assumed available (it is,
 * given RECORD_AUDIO) so we don't hold the mic just to test it.
 */
class AudioRecordTierProbe : TierProbe {

    @SuppressLint("MissingPermission")
    override fun isAvailable(tier: CaptureTier): Boolean {
        if (tier == CaptureTier.MIC_ONLY) return true

        val (source, channelConfig) = when (tier) {
            CaptureTier.STEREO -> Src.VOICE_CALL to STEREO
            CaptureTier.DUAL_MONO -> Src.VOICE_RECOGNITION to MONO
            CaptureTier.MIC_ONLY -> return true
        }

        val minBytes = AudioRecord.getMinBufferSize(
            AudioSource.SAMPLE_RATE,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBytes <= 0) return false

        val record = try {
            AudioRecord(
                source,
                AudioSource.SAMPLE_RATE,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                minBytes,
            )
        } catch (e: Exception) {
            return false
        }

        return try {
            if (record.state != AudioRecord.STATE_INITIALIZED) return false
            record.startRecording()
            val started = record.recordingState == AudioRecord.RECORDSTATE_RECORDING
            runCatching { record.stop() }
            started
        } catch (e: Exception) {
            false
        } finally {
            record.release()
        }
    }
}
