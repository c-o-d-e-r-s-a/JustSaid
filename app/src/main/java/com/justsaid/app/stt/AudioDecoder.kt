package com.justsaid.app.stt

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject

/**
 * Header metadata for a PCM WAV file produced by JustSaid capture or test fixtures.
 */
data class WavInfo(
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val dataOffset: Long,
    val frameCount: Int,
) {
    val durationMs: Long get() = if (sampleRate <= 0) 0L else frameCount * 1000L / sampleRate
}

/**
 * Minimal RIFF/WAVE reader for the app's own recordings (16-bit PCM mono/stereo,
 * written by Phase 2's WavWriter) plus test fixtures. Reads bounded windows only —
 * never loads an entire recording into a [ByteArray] or full-session [FloatArray].
 */
class AudioDecoder @Inject constructor() {

    /**
     * Parses the WAV header and locates the PCM data chunk without reading audio bytes.
     */
    fun probe(file: File): WavInfo {
        val (header, dataOffset, dataSize) = readWavHeader(file)
        val bytesPerFrame = header.channels * (header.bitsPerSample / 8)
        val frameCount = if (bytesPerFrame == 0) 0 else dataSize / bytesPerFrame
        return WavInfo(
            channels = header.channels,
            sampleRate = header.sampleRate,
            bitsPerSample = header.bitsPerSample,
            dataOffset = dataOffset,
            frameCount = frameCount,
        )
    }

    /**
     * Decodes [frameCount] source frames starting at [startFrame] into mono float PCM
     * at [WhisperParams.SAMPLE_RATE_HZ]. Stereo input is downmixed (L+R)/2 with no
     * channel attribution (Constitution S1).
     */
    fun decodeMonoWindow(
        file: File,
        info: WavInfo,
        startFrame: Int,
        frameCount: Int,
    ): FloatArray {
        if (frameCount <= 0) return FloatArray(0)
        val bytesPerFrame = info.channels * (info.bitsPerSample / 8)
        val startByte = info.dataOffset + startFrame.toLong() * bytesPerFrame
        val bytesToRead = frameCount * bytesPerFrame
        val data = ByteArray(bytesToRead)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(startByte)
            raf.readFully(data)
        }

        val mono = when (info.channels) {
            1 -> pcm16ToMono(data)
            2 -> {
                val frames = data.size / bytesPerFrame
                val out = FloatArray(frames)
                for (f in 0 until frames) {
                    val loL = data[f * 4].toInt() and 0xFF
                    val hiL = data[f * 4 + 1].toInt()
                    val loR = data[f * 4 + 2].toInt() and 0xFF
                    val hiR = data[f * 4 + 3].toInt()
                    val left = ((hiL shl 8) or loL) / 32768f
                    val right = ((hiR shl 8) or loR) / 32768f
                    out[f] = (left + right) / 2f
                }
                out
            }
            else -> throw IOException("unsupported channel count ${info.channels}")
        }
        return resampleTo16k(mono, info.sampleRate)
    }

    private fun pcm16ToMono(data: ByteArray): FloatArray {
        val samples = FloatArray(data.size / 2)
        for (i in samples.indices) {
            val lo = data[i * 2].toInt() and 0xFF
            val hi = data[i * 2 + 1].toInt()
            samples[i] = ((hi shl 8) or lo) / 32768f
        }
        return samples
    }

    private data class WavHeader(val channels: Int, val sampleRate: Int, val bitsPerSample: Int)

    private fun readWavHeader(file: File): Triple<WavHeader, Long, Int> {
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            var offset = 0L
            if (readTag(input).also { offset += 4 } != "RIFF") {
                throw IOException("not a RIFF file: ${file.name}")
            }
            input.skipFully(4)
            offset += 4
            if (readTag(input).also { offset += 4 } != "WAVE") {
                throw IOException("not a WAVE file: ${file.name}")
            }

            var header: WavHeader? = null
            var dataOffset = 0L
            var dataSize = 0
            while (true) {
                val tag = readTagOrNull(input) ?: break
                offset += 4
                val size = readLe32(input)
                offset += 4
                when (tag) {
                    "fmt " -> {
                        val format = readLe16(input)
                        val channels = readLe16(input)
                        val sampleRate = readLe32(input)
                        input.skipFully(6)
                        val bits = readLe16(input)
                        input.skipFully(size - 16)
                        offset += size
                        if (format != 1) throw IOException("unsupported WAV format $format (PCM only)")
                        if (bits != 16) throw IOException("unsupported bit depth $bits (16-bit only)")
                        if (channels !in 1..2) throw IOException("unsupported channel count $channels")
                        header = WavHeader(channels, sampleRate, bits)
                    }
                    "data" -> {
                        header ?: throw IOException("data chunk before fmt chunk")
                        dataOffset = offset
                        dataSize = size
                        input.skipFully(size)
                        offset += size
                    }
                    else -> {
                        val padded = size + (size and 1)
                        input.skipFully(padded)
                        offset += padded
                    }
                }
            }
            val h = header ?: throw IOException("no fmt chunk in ${file.name}")
            if (dataSize == 0) throw IOException("no data chunk in ${file.name}")
            return Triple(h, dataOffset, dataSize)
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
