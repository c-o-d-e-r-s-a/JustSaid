package com.justsaid.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class WavWriterTest {

    private lateinit var file: File

    @Before
    fun setUp() {
        file = File.createTempFile("wavwriter", ".wav")
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `mono header has correct format fields`() {
        val samples = shortArrayOf(0, 100, -100, 32767, -32768)
        WavWriter(file, channels = 1).apply {
            open()
            write(samples)
            close()
        }

        val bytes = file.readBytes()
        assertThat(ascii(bytes, 0, 4)).isEqualTo("RIFF")
        assertThat(ascii(bytes, 8, 4)).isEqualTo("WAVE")
        assertThat(ascii(bytes, 12, 4)).isEqualTo("fmt ")
        assertThat(le16(bytes, 20)).isEqualTo(1)             // PCM
        assertThat(le16(bytes, 22)).isEqualTo(1)             // channels
        assertThat(le32(bytes, 24)).isEqualTo(16_000)        // sample rate
        assertThat(le16(bytes, 34)).isEqualTo(16)            // bits per sample
        assertThat(ascii(bytes, 36, 4)).isEqualTo("data")

        val dataSize = le32(bytes, 40)
        assertThat(dataSize).isEqualTo(samples.size * 2)
        assertThat(le32(bytes, 4)).isEqualTo(36 + dataSize)  // RIFF chunk size
    }

    @Test
    fun `mono samples round-trip little-endian`() {
        val samples = shortArrayOf(1, 258, -1)
        WavWriter(file, channels = 1).apply { open(); write(samples); close() }

        val bytes = file.readBytes()
        for (i in samples.indices) {
            assertThat(le16Signed(bytes, 44 + i * 2)).isEqualTo(samples[i].toInt())
        }
    }

    @Test
    fun `stereo preserves interleaved L R and byte rate`() {
        // L0,R0,L1,R1 interleaved as delivered by a Tier-1 source.
        val interleaved = shortArrayOf(10, -10, 20, -20)
        WavWriter(file, channels = 2).apply { open(); write(interleaved); close() }

        val bytes = file.readBytes()
        assertThat(le16(bytes, 22)).isEqualTo(2)                      // channels
        assertThat(le32(bytes, 28)).isEqualTo(16_000 * 2 * 2)         // byte rate
        assertThat(le16(bytes, 32)).isEqualTo(4)                      // block align = 2ch * 2 bytes
        assertThat(le32(bytes, 40)).isEqualTo(interleaved.size * 2)   // data size
        for (i in interleaved.indices) {
            assertThat(le16Signed(bytes, 44 + i * 2)).isEqualTo(interleaved[i].toInt())
        }
    }

    @Test
    fun `multiple writes accumulate data size`() {
        WavWriter(file, channels = 1).apply {
            open()
            write(shortArrayOf(1, 2, 3))
            write(shortArrayOf(4, 5))
            close()
        }
        assertThat(le32(file.readBytes(), 40)).isEqualTo(5 * 2)
    }

    private fun ascii(b: ByteArray, off: Int, len: Int): String =
        String(b, off, len, Charsets.US_ASCII)

    private fun le16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun le16Signed(b: ByteArray, off: Int): Int = le16(b, off).toShort().toInt()

    private fun le32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
}
