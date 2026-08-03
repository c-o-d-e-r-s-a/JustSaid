package com.justsaid.app.stt

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.audio.CaptureInput
import com.justsaid.app.audio.RecordedSession
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
    fun `mono 16k window decodes to normalized floats`() {
        writeWav(channels = 1, samples = shortArrayOf(0, 16384, -16384, 32767, -32768))

        val info = decoder.probe(file)
        val mono = decoder.decodeMonoWindow(file, info, startFrame = 0, frameCount = info.frameCount)

        assertThat(info.channels).isEqualTo(1)
        assertThat(info.sampleRate).isEqualTo(16_000)
        assertThat(mono.size).isEqualTo(5)
        assertThat(mono[0]).isWithin(1e-4f).of(0f)
        assertThat(mono[1]).isWithin(1e-4f).of(0.5f)
        assertThat(mono[2]).isWithin(1e-4f).of(-0.5f)
        assertThat(mono[3]).isWithin(1e-4f).of(0.99997f)
        assertThat(mono[4]).isWithin(1e-4f).of(-1f)
    }

    @Test
    fun `stereo wav downmixes to mono without channel split`() {
        writeWav(channels = 2, samples = shortArrayOf(100, -100, 200, -200, 300, -300))

        val info = decoder.probe(file)
        val mono = decoder.decodeMonoWindow(file, info, startFrame = 0, frameCount = info.frameCount)

        assertThat(info.channels).isEqualTo(2)
        assertThat(mono.size).isEqualTo(3)
        assertThat(mono[0]).isWithin(1e-5f).of(0f)
        assertThat(mono[1]).isWithin(1e-5f).of(0f)
        assertThat(mono[2]).isWithin(1e-5f).of(0f)
    }

    @Test
    fun `8k input is resampled to 16k with doubled length`() {
        val input = ShortArray(800) { (1000 * (it % 8)).toShort() }
        writeWav(channels = 1, samples = input, sampleRate = 8_000)

        val info = decoder.probe(file)
        val mono = decoder.decodeMonoWindow(file, info, startFrame = 0, frameCount = info.frameCount)

        assertThat(mono.size).isEqualTo(1600)
        assertThat(mono.max()).isAtMost(7000f / 32768f + 1e-4f)
        assertThat(mono.min()).isAtLeast(-1e-4f)
    }

    @Test
    fun `bounded windows read only the requested slice`() {
        val samples = ShortArray(16_000) { (it % 256).toShort() }
        writeWav(channels = 1, samples = samples, sampleRate = 16_000)
        val info = decoder.probe(file)

        val first = decoder.decodeMonoWindow(file, info, startFrame = 0, frameCount = 4_000)
        val second = decoder.decodeMonoWindow(file, info, startFrame = 4_000, frameCount = 4_000)

        assertThat(first.size).isEqualTo(4_000)
        assertThat(second.size).isEqualTo(4_000)
        assertThat(first[0]).isWithin(1e-5f).of(0f)
        assertThat(second[0]).isWithin(1e-5f).of(samples[4_000] / 32768f)
    }

    @Test
    fun `non-wav content is rejected`() {
        file.writeBytes(ByteArray(64) { 0x42 })
        assertThrows(IOException::class.java) { decoder.probe(file) }
    }

    @Test
    fun `wav with extra chunk before data still probes and decodes`() {
        writeWav(channels = 1, samples = shortArrayOf(1, 2, 3))
        val bytes = file.readBytes()
        val list = byteArrayOf('L'.code.toByte(), 'I'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(), 4, 0, 0, 0, 1, 2, 3, 4)
        file.writeBytes(bytes.copyOfRange(0, 36) + list + bytes.copyOfRange(36, bytes.size))

        val info = decoder.probe(file)
        assertThat(decoder.decodeMonoWindow(file, info, 0, info.frameCount).size).isEqualTo(3)
    }

    private fun writeWav(channels: Int, samples: ShortArray, sampleRate: Int = 16_000) {
        WavWriter(file, channels = channels, sampleRate = sampleRate).apply {
            open()
            write(samples)
            close()
        }
    }
}
