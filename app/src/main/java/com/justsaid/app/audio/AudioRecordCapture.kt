package com.justsaid.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Shared `AudioRecord` capture used by every tier. Differences between tiers are just
 * the [audioSource] constant and [channelConfig]; the read loop is identical.
 *
 * The RECORD_AUDIO permission is enforced upstream (the foreground service is only started
 * for an active call after the dialer role + permission are granted); a missing permission
 * or unsupported config surfaces as an empty flow rather than a crash (E1/E2).
 */
@SuppressLint("MissingPermission")
internal fun audioRecordFrames(
    audioSource: Int,
    channelConfig: Int,
    dispatcher: CoroutineDispatcher,
): Flow<ShortArray> = flow {
    val minBytes = AudioRecord.getMinBufferSize(
        AudioSource.SAMPLE_RATE,
        channelConfig,
        AudioFormat.ENCODING_PCM_16BIT,
    )
    if (minBytes <= 0) return@flow

    // Buffer ~4x the minimum so short scheduler stalls don't drop samples.
    val bufferBytes = minBytes * 4
    val record = try {
        AudioRecord(
            audioSource,
            AudioSource.SAMPLE_RATE,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
    } catch (e: Exception) {
        return@flow
    }

    if (record.state != AudioRecord.STATE_INITIALIZED) {
        record.release()
        return@flow
    }

    val frame = ShortArray(bufferBytes / 2)
    try {
        record.startRecording()
        while (currentCoroutineContext().isActive) {
            val read = record.read(frame, 0, frame.size)
            when {
                read > 0 -> emit(frame.copyOf(read))
                read < 0 -> break
            }
        }
    } finally {
        runCatching { record.stop() }
        record.release()
    }
}.flowOn(dispatcher)

internal const val MONO: Int = AudioFormat.CHANNEL_IN_MONO
internal const val STEREO: Int = AudioFormat.CHANNEL_IN_STEREO

/** Named for readability at call sites. */
internal object Src {
    const val VOICE_CALL = MediaRecorder.AudioSource.VOICE_CALL
    const val VOICE_RECOGNITION = MediaRecorder.AudioSource.VOICE_RECOGNITION
    const val VOICE_COMMUNICATION = MediaRecorder.AudioSource.VOICE_COMMUNICATION
    const val MIC = MediaRecorder.AudioSource.MIC
}

/** Tier-2 sources to try in order; first with live PCM during a call wins. */
internal val DUAL_MONO_SOURCES: IntArray = intArrayOf(
    Src.VOICE_RECOGNITION,
    Src.VOICE_COMMUNICATION,
)
