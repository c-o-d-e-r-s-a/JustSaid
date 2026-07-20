package com.justsaid.app.stt

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.audio.WavWriter
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class AudioDecoderTest {

    private val decoder = AudioDecoder()
    private lateinit var file: File

    @Before
    fun setUp() {
        file = File.createTempFile("decoder", ".wav")
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `mono 16k wav decodes to normalized floats`() {
        writeWav(channels = 1, samples = shortArrayOf(0, 16384, -16384, 32767, -32768))

        val audio = decoder.decode(file)

        assertThat(audio.isStereo).isFalse()
        assertThat(audio.left).isNull()
        assertThat(audio.right).isNull()
        assertThat(audio.sampleRate).isEqualTo(16_000)
        assertThat(audio.mono.size).isEqualTo(5)
        assertThat(audio.mono[0]).isWithin(1e-4f).of(0f)
        assertThat(audio.mono[1]).isWithin(1e-4f).of(0.5f)
        assertThat(audio.mono[2]).isWithin(1e-4f).of(-0.5f)
        assertThat(audio.mono[3]).isWithin(1e-4f).of(0.99997f)
        assertThat(audio.mono[4]).isWithin(1e-4f).of(-1f)
    }

    @Test
    fun `stereo wav splits L and R channels correctly`() {
        // Interleaved L,R: L ramps positive, R ramps negative.
        writeWav(channels = 2, samples = shortArrayOf(100, -100, 200, -200, 300, -300))

        val audio = decoder.decode(file)

        assertThat(audio.isStereo).isTrue()
        val left = audio.left!!
        val right = audio.right!!
        assertThat(left.size).isEqualTo(3)
        assertThat(right.size).isEqualTo(3)
        for (i in 0 until 3) {
            assertThat(left[i]).isWithin(1e-5f).of((100f * (i + 1)) / 32768f)
            assertThat(right[i]).isWithin(1e-5f).of((-100f * (i + 1)) / 32768f)
        }
        // Downmix is the L/R average.
        for (i in audio.mono.indices) {
            assertThat(audio.mono[i]).isWithin(1e-5f).of((left[i] + right[i]) / 2f)
        }
    }

    @Test
    fun `8k input is resampled to 16k with doubled length`() {
        val input = ShortArray(800) { (1000 * (it % 8)).toShort() }
        writeWav(channels = 1, samples = input, sampleRate = 8_000)

        val audio = decoder.decode(file)

        assertThat(audio.sampleRate).isEqualTo(16_000)
        assertThat(audio.mono.size).isEqualTo(1600)
        // Interpolated values stay within the input's dynamic range.
        assertThat(audio.mono.max()).isAtMost(7000f / 32768f + 1e-4f)
        assertThat(audio.mono.min()).isAtLeast(-1e-4f)
    }

    @Test
    fun `non-wav content is rejected`() {
        file.writeBytes(ByteArray(64) { 0x42 })
        assertThrows(IOException::class.java) { decoder.decode(file) }
    }

    @Test
    fun `wav with extra chunk before data still decodes`() {
        writeWav(channels = 1, samples = shortArrayOf(1, 2, 3))
        // Splice a LIST chunk between fmt and data (byte 36 in WavWriter's layout).
        val bytes = file.readBytes()
        val list = byteArrayOf('L'.code.toByte(), 'I'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(), 4, 0, 0, 0, 1, 2, 3, 4)
        file.writeBytes(bytes.copyOfRange(0, 36) + list + bytes.copyOfRange(36, bytes.size))

        assertThat(decoder.decode(file).mono.size).isEqualTo(3)
    }

    private fun writeWav(channels: Int, samples: ShortArray, sampleRate: Int = 16_000) {
        WavWriter(file, channels = channels, sampleRate = sampleRate).apply {
            open()
            write(samples)
            close()
        }
    }
}
