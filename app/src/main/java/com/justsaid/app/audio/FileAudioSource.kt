package com.justsaid.app.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Test/debug capture source that streams PCM from a fixture wav instead of the mic. This
 * is the linchpin of device-free testing (TESTING.md §B.3): the Espresso `InCallToggleTest`
 * binds this via a Hilt test module so the full toggle -> capture -> disconnect flow runs on
 * an emulator with no real call. Skips the 44-byte RIFF header and emits ~100 ms chunks.
 */
class FileAudioSource(
    private val fixture: File,
    override val tier: CaptureTier = CaptureTier.MIC_ONLY,
    override val channels: Int = 1,
    private val chunkFrames: Int = AudioSource.SAMPLE_RATE / 10,
) : AudioSource {

    override fun frames(): Flow<ShortArray> = flow {
        if (!fixture.exists()) return@flow
        val bytes = fixture.readBytes()
        val start = if (bytes.size > 44) 44 else bytes.size // skip wav header if present
        val totalSamples = (bytes.size - start) / 2
        var i = 0
        while (i < totalSamples) {
            val n = minOf(chunkFrames, totalSamples - i)
            val chunk = ShortArray(n)
            for (j in 0 until n) {
                val off = start + (i + j) * 2
                val lo = bytes[off].toInt() and 0xFF
                val hi = bytes[off + 1].toInt()
                chunk[j] = ((hi shl 8) or lo).toShort()
            }
            emit(chunk)
            i += n
            delay(10) // pace like a live stream so cancellation is observable
        }
    }
}
