package com.justsaid.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord

/**
 * Real capability probe: tries to construct and briefly start an `AudioRecord` for the
 * tier's source/channel config, then reads a short window and requires actual signal.
 * The read check matters: some OEMs (observed on Samsung One UI) let a privileged-ish
 * source like `VOICE_RECOGNITION` construct and start during a call but feed it pure
 * digital silence — start success alone would select a tier that records only zeros.
 * If construction, start, or the signal check fails the tier is unavailable and the
 * factory falls through. MIC is assumed available (it is, given RECORD_AUDIO) so we
 * don't hold the mic just to test it.
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
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return false
            probeHasLiveSignal { buffer -> record.read(buffer, 0, buffer.size) }
        } catch (e: Exception) {
            false
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }
}

/** How much audio the probe listens to before declaring a tier silent. */
internal const val PROBE_WINDOW_MS = 250

/**
 * Drains ~[PROBE_WINDOW_MS] of PCM through [read] and reports whether any non-zero
 * sample arrived. Real capture always carries at least noise-floor dither, while an
 * OEM-muted stream delivers exact zeros — so a single non-zero sample is proof of
 * life. Extracted from the `AudioRecord` plumbing so the decision is JVM-testable.
 * A non-positive read result means the stream died; that also counts as no signal.
 */
internal fun probeHasLiveSignal(
    samplesToRead: Int = AudioSource.SAMPLE_RATE * PROBE_WINDOW_MS / 1000,
    read: (ShortArray) -> Int,
): Boolean {
    val buffer = ShortArray(512)
    var remaining = samplesToRead
    while (remaining > 0) {
        val n = read(buffer)
        if (n <= 0) return false
        for (i in 0 until n) {
            if (buffer[i] != 0.toShort()) return true
        }
        remaining -= n
    }
    return false
}
