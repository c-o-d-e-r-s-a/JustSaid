package com.justsaid.app.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * Streams 16-bit PCM into a canonical RIFF/WAVE file (Constitution A3: 16 kHz, 16-bit;
 * stereo only when the capture tier provides L/R separation). A 44-byte placeholder header
 * is written up front and patched with the real sizes on [close], so we never buffer the
 * whole recording in memory.
 *
 * Stereo frames are expected already interleaved (L,R,L,R…) by the Tier-1 source; this
 * writer persists samples verbatim and only records [channels] in the header.
 *
 * Pure java.io — no Android types — so it runs in JVM unit tests.
 */
class WavWriter(
    private val file: File,
    private val channels: Int,
    private val sampleRate: Int = AudioSource.SAMPLE_RATE,
) {
    private var raf: RandomAccessFile? = null
    var dataBytes: Long = 0L
        private set

    fun open() {
        require(channels == 1 || channels == 2) { "channels must be 1 or 2, was $channels" }
        file.parentFile?.mkdirs()
        raf = RandomAccessFile(file, "rw").apply {
            setLength(0)
            write(ByteArray(HEADER_SIZE)) // placeholder, patched on close
        }
        dataBytes = 0L
    }

    /** Appends one PCM frame. Samples are written little-endian. */
    fun write(frame: ShortArray, count: Int = frame.size) {
        val out = raf ?: error("WavWriter.write called before open()")
        val bytes = ByteArray(count * 2)
        var b = 0
        for (i in 0 until count) {
            val s = frame[i].toInt()
            bytes[b++] = (s and 0xFF).toByte()
            bytes[b++] = ((s shr 8) and 0xFF).toByte()
        }
        out.write(bytes)
        dataBytes += bytes.size
    }

    /** Patches the header with final sizes and closes the file. Idempotent. */
    fun close() {
        val out = raf ?: return
        raf = null
        try {
            out.seek(0)
            out.write(header(dataBytes.toInt()))
        } finally {
            out.close()
        }
    }

    private fun header(dataSize: Int): ByteArray {
        val byteRate = sampleRate * channels * (BITS_PER_SAMPLE / 8)
        val blockAlign = channels * (BITS_PER_SAMPLE / 8)
        val h = ByteArray(HEADER_SIZE)
        var p = 0
        fun str(s: String) { for (c in s) h[p++] = c.code.toByte() }
        fun le32(v: Int) {
            h[p++] = (v and 0xFF).toByte()
            h[p++] = ((v shr 8) and 0xFF).toByte()
            h[p++] = ((v shr 16) and 0xFF).toByte()
            h[p++] = ((v shr 24) and 0xFF).toByte()
        }
        fun le16(v: Int) {
            h[p++] = (v and 0xFF).toByte()
            h[p++] = ((v shr 8) and 0xFF).toByte()
        }
        str("RIFF"); le32(36 + dataSize); str("WAVE")
        str("fmt "); le32(16); le16(1); le16(channels)
        le32(sampleRate); le32(byteRate); le16(blockAlign); le16(BITS_PER_SAMPLE)
        str("data"); le32(dataSize)
        return h
    }

    /** Total PCM samples written per channel-frame; used for duration reporting. */
    fun durationMs(): Long {
        val bytesPerMs = sampleRate * channels * (BITS_PER_SAMPLE / 8) / 1000.0
        return if (bytesPerMs > 0) (dataBytes / bytesPerMs).toLong() else 0L
    }

    private companion object {
        const val HEADER_SIZE = 44
        const val BITS_PER_SAMPLE = 16
    }
}
