package com.justsaid.app.stt

import java.io.DataInputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Decoded PCM ready for whisper: normalized floats in [-1, 1] at 16 kHz.
 * [left] and [right] are non-null only for stereo input (Tier 1 capture),
 * where L = local user and R = remote party (Constitution S1).
 */
data class DecodedAudio(
    val mono: FloatArray,
    val left: FloatArray?,
    val right: FloatArray?,
    val sampleRate: Int,
) {
    val isStereo: Boolean get() = left != null && right != null
}

/**
 * Minimal RIFF/WAVE reader for the app's own recordings (16-bit PCM mono/stereo,
 * written by Phase 2's WavWriter) plus test fixtures. Converts to float, splits
 * stereo channels, and linearly resamples anything not already at 16 kHz.
 */
class AudioDecoder @Inject constructor() {

    /**
     * Reads [file] fully into normalized float PCM at [WhisperParams.SAMPLE_RATE_HZ].
     * Throws [IOException] on malformed input — callers (the engine) convert this
     * into a JustSaidResult.Failure at the layer boundary.
     */
    fun decode(file: File): DecodedAudio {
        val (header, data) = readWav(file)

        val samples = FloatArray(data.size / 2)
        for (i in samples.indices) {
            val lo = data[i * 2].toInt() and 0xFF
            val hi = data[i * 2 + 1].toInt()
            samples[i] = ((hi shl 8) or lo) / 32768f
        }

        return if (header.channels == 2) {
            val frames = samples.size / 2
            val left = FloatArray(frames)
            val right = FloatArray(frames)
            for (f in 0 until frames) {
                left[f] = samples[f * 2]
                right[f] = samples[f * 2 + 1]
            }
            val l16 = resampleTo16k(left, header.sampleRate)
            val r16 = resampleTo16k(right, header.sampleRate)
            val mono = FloatArray(l16.size) { (l16[it] + r16[it]) / 2f }
            DecodedAudio(mono, l16, r16, WhisperParams.SAMPLE_RATE_HZ)
        } else {
            DecodedAudio(resampleTo16k(samples, header.sampleRate), null, null, WhisperParams.SAMPLE_RATE_HZ)
        }
    }

    private data class WavHeader(val channels: Int, val sampleRate: Int, val bitsPerSample: Int)

    private fun readWav(file: File): Pair<WavHeader, ByteArray> {
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (readTag(input) != "RIFF") throw IOException("not a RIFF file: ${file.name}")
            input.skipFully(4) // riff size
            if (readTag(input) != "WAVE") throw IOException("not a WAVE file: ${file.name}")

            var header: WavHeader? = null
            // Chunk-walk: fixtures from other tools may carry LIST/INFO chunks.
            while (true) {
                val tag = readTagOrNull(input) ?: break
                val size = readLe32(input)
                when (tag) {
                    "fmt " -> {
                        val format = readLe16(input)
                        val channels = readLe16(input)
                        val sampleRate = readLe32(input)
                        input.skipFully(6) // byteRate + blockAlign
                        val bits = readLe16(input)
                        input.skipFully(size - 16)
                        if (format != 1) throw IOException("unsupported WAV format $format (PCM only)")
                        if (bits != 16) throw IOException("unsupported bit depth $bits (16-bit only)")
                        if (channels !in 1..2) throw IOException("unsupported channel count $channels")
                        header = WavHeader(channels, sampleRate, bits)
                    }
                    "data" -> {
                        val h = header ?: throw IOException("data chunk before fmt chunk")
                        val data = ByteArray(size)
                        input.readFully(data)
                        return h to data
                    }
                    else -> input.skipFully(size + (size and 1)) // chunks are word-aligned
                }
            }
            throw IOException("no data chunk in ${file.name}")
        }
    }

    /** Linear interpolation resample; identity when already at 16 kHz (the normal case). */
    private fun resampleTo16k(input: FloatArray, fromRate: Int): FloatArray {
        val target = WhisperParams.SAMPLE_RATE_HZ
        if (fromRate == target || input.isEmpty()) return input
        val outLength = (input.size.toLong() * target / fromRate).toInt()
        val out = FloatArray(outLength)
        val step = fromRate.toDouble() / target
        for (i in 0 until outLength) {
            val pos = i * step
            val i0 = pos.toInt().coerceAtMost(input.size - 1)
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = (pos - i0).toFloat()
            out[i] = input[i0] * (1f - frac) + input[i1] * frac
        }
        return out
    }

    private fun readTag(input: DataInputStream): String =
        readTagOrNull(input) ?: throw IOException("unexpected end of file")

    private fun readTagOrNull(input: DataInputStream): String? {
        val bytes = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(bytes, read, 4 - read)
            if (n < 0) return if (read == 0) null else throw IOException("truncated chunk tag")
            read += n
        }
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readLe16(input: DataInputStream): Int {
        val b0 = input.readUnsignedByte()
        val b1 = input.readUnsignedByte()
        return (b1 shl 8) or b0
    }

    private fun readLe32(input: DataInputStream): Int {
        val b0 = input.readUnsignedByte()
        val b1 = input.readUnsignedByte()
        val b2 = input.readUnsignedByte()
        val b3 = input.readUnsignedByte()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    private fun DataInputStream.skipFully(bytes: Int) {
        var remaining = bytes.toLong()
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) throw IOException("truncated WAV chunk")
            remaining -= skipped
        }
    }
}
