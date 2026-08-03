package com.justsaid.app.stt

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.audio.CaptureInput
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.audio.WavWriter
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.ModelPaths
import com.justsaid.app.core.Speaker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Kotlin-wrapper contract tests with a fake [NativeWhisperBridge] — no `.so`, no
 * device (TESTING.md C.1). Covers chunking, overlap dedupe, mono UNKNOWN tagging,
 * VAD silence skip, bounded decode, max-duration guard, and the single-init/single-free
 * native lifecycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WhisperEngineFakeTest {

    private val sampleRate = 16_000
    private val params = WhisperParams(chunkSeconds = 2, overlapSeconds = 1)

    private lateinit var modelFile: File
    private lateinit var wavFile: File
    private lateinit var bridge: FakeBridge

    private class FakeModelPaths(private val stt: File) : ModelPaths {
        override fun sttModelFile(): File = stt
        override fun llmModelFile(): File = stt
        override fun modelsReady(): Boolean = stt.exists()
    }

    private class FakeBridge : NativeWhisperBridge {
        var initCalls = 0
        var freeCalls = 0
        var failInit = false
        val windows = mutableListOf<FloatArray>()
        val languages = mutableListOf<String>()

        /** Returns JSON for the Nth transcribe call; default is one unique segment. */
        var responseFor: (callIndex: Int) -> String = { i ->
            """{"lang":"auto","segments":[{"t0":1200,"t1":1900,"text":"seg$i"}]}"""
        }

        override fun init(modelPath: String, threads: Int): Long {
            initCalls++
            return if (failInit) 0L else 42L
        }

        override fun transcribe(
            handle: Long,
            pcm: FloatArray,
            lang: String,
            translate: Boolean,
            allowedLanguagesCsv: String,
        ): String {
            check(handle == 42L) { "transcribe with wrong handle" }
            check(freeCalls == 0) { "transcribe after free (N1 violation)" }
            windows += pcm
            languages += lang
            return responseFor(windows.size - 1)
        }

        override fun free(handle: Long) {
            freeCalls++
        }
    }

    @Before
    fun setUp() {
        modelFile = File.createTempFile("ggml-model", ".bin").apply { writeBytes(ByteArray(16)) }
        wavFile = File.createTempFile("session", ".wav")
        bridge = FakeBridge()
    }

    @After
    fun tearDown() {
        modelFile.delete()
        wavFile.delete()
    }

    // ── chunking ──

    @Test
    fun `5s audio with 2s windows and 1s overlap produces 4 windows on one context`() = runTest {
        writeMonoWav(speech(seconds = 5))

        val result = engine().transcribe(session(), "auto")

        assertThat(result).isInstanceOf(JustSaidResult.Success::class.java)
        assertThat(bridge.windows).hasSize(4)
        bridge.windows.forEach { assertThat(it.size).isAtMost(params.chunkSamples) }
        assertThat(bridge.initCalls).isEqualTo(1)
        assertThat(bridge.freeCalls).isEqualTo(1)
        assertThat(bridge.languages).containsExactly("auto", "auto", "auto", "auto")
    }

    @Test
    fun `segment times are offset by their window start`() = runTest {
        writeMonoWav(speech(seconds = 3))
        val result = engine().transcribe(session(), "en") as JustSaidResult.Success

        val starts = result.value.segments.map { it.startMs }
        assertThat(starts).containsExactly(1200L, 2200L).inOrder()
    }

    // ── overlap dedupe ──

    @Test
    fun `segments inside the re-heard overlap zone are dropped`() = runTest {
        writeMonoWav(speech(seconds = 3))
        bridge.responseFor = { i ->
            """{"lang":"auto","segments":[{"t0":100,"t1":600,"text":"overlap echo"},{"t0":1300,"t1":1800,"text":"unique $i"}]}"""
        }

        val result = engine().transcribe(session(), "auto") as JustSaidResult.Success
        val texts = result.value.segments.map { it.text }

        assertThat(texts).containsExactly("overlap echo", "unique 0", "unique 1").inOrder()
    }

    @Test
    fun `identical consecutive lines are deduped`() = runTest {
        writeMonoWav(speech(seconds = 3))
        bridge.responseFor = { """{"lang":"auto","segments":[{"t0":1300,"t1":1800,"text":"  Same LINE  "}]}""" }

        val result = engine().transcribe(session(), "auto") as JustSaidResult.Success

        assertThat(result.value.segments.map { it.text }).containsExactly("Same LINE")
    }

    // ── speaker tagging ──

    @Test
    fun `mono session tags every segment UNKNOWN`() = runTest {
        writeMonoWav(speech(seconds = 3))

        val result = engine().transcribe(session(), "auto")
        val segments = (result as JustSaidResult.Success).value.segments

        assertThat(segments).isNotEmpty()
        assertThat(segments.map { it.speaker }.toSet()).containsExactly(Speaker.UNKNOWN)
    }

    @Test
    fun `stereo fixture downmixes and tags every segment UNKNOWN`() = runTest {
        writeStereoWav(seconds = 3)

        val result = engine().transcribe(session(channels = 2), "auto")
        val segments = (result as JustSaidResult.Success).value.segments

        assertThat(bridge.windows).hasSize(2)
        assertThat(segments).isNotEmpty()
        assertThat(segments.map { it.speaker }.toSet()).containsExactly(Speaker.UNKNOWN)
    }

    // ── VAD ──

    @Test
    fun `silent windows are skipped without a native call`() = runTest {
        val pcm = FloatArray(4 * sampleRate)
        speech(seconds = 2).copyInto(pcm, destinationOffset = 2 * sampleRate)
        writeMonoWav(pcm)

        val result = engine().transcribe(session(), "auto")

        assertThat(result).isInstanceOf(JustSaidResult.Success::class.java)
        assertThat(bridge.windows).hasSize(2)
    }

    @Test
    fun `fully silent session yields an empty transcript not an error`() = runTest {
        writeMonoWav(FloatArray(3 * sampleRate))

        val result = engine().transcribe(session(), "auto") as JustSaidResult.Success

        assertThat(result.value.segments).isEmpty()
        assertThat(bridge.windows).isEmpty()
    }

    @Test
    fun `auto mode pins whisper language after first detected window`() = runTest {
        writeMonoWav(speech(seconds = 5))
        bridge.responseFor = { i ->
            val lang = if (i == 0) "hi" else "auto"
            """{"lang":"$lang","segments":[{"t0":1200,"t1":1900,"text":"seg$i"}]}"""
        }

        engine().transcribe(session(), "auto")

        assertThat(bridge.languages).containsExactly("auto", "hi", "hi", "hi")
    }

    @Test
    fun `detected language is attached to transcript`() = runTest {
        writeMonoWav(speech(seconds = 2))
        bridge.responseFor = {
            """{"lang":"hi","segments":[{"t0":1200,"t1":1900,"text":"नमस्ते"}]}"""
        }

        val result = engine().transcribe(session(), "auto") as JustSaidResult.Success

        assertThat(result.value.detectedLanguage).isEqualTo("hi")
    }

    // ── duration guard ──

    @Test
    fun `session longer than max duration fails without native init`() = runTest {
        writeMonoWav(speech(seconds = 2))

        val result = engine().transcribe(
            session(durationMs = WhisperParams.MAX_SESSION_DURATION_MS + 1),
            "auto",
        )

        assertThat(result).isInstanceOf(JustSaidResult.Failure::class.java)
        assertThat((result as JustSaidResult.Failure).reason).contains("too long")
        assertThat(bridge.initCalls).isEqualTo(0)
    }

    // ── failure paths ──

    @Test
    fun `missing model file fails without touching native`() = runTest {
        writeMonoWav(speech(seconds = 2))
        modelFile.delete()

        val result = engine().transcribe(session(), "auto")

        assertThat(result).isInstanceOf(JustSaidResult.Failure::class.java)
        assertThat(bridge.initCalls).isEqualTo(0)
    }

    @Test
    fun `native init failure surfaces as Failure and never frees`() = runTest {
        writeMonoWav(speech(seconds = 2))
        bridge.failInit = true

        val result = engine().transcribe(session(), "auto")

        assertThat(result).isInstanceOf(JustSaidResult.Failure::class.java)
        assertThat(bridge.freeCalls).isEqualTo(0)
    }

    @Test
    fun `unreadable wav fails cleanly`() = runTest {
        wavFile.writeBytes(ByteArray(32) { 7 })

        val result = engine().transcribe(session(), "auto")

        assertThat(result).isInstanceOf(JustSaidResult.Failure::class.java)
        assertThat((result as JustSaidResult.Failure).reason).contains("decode")
    }

    // ── helpers ──

    private fun engine() = WhisperEngine(
        modelPaths = FakeModelPaths(modelFile),
        audioDecoder = AudioDecoder(),
        vadGate = VadGate(),
        dispatcher = UnconfinedTestDispatcher(),
        params = params,
        bridgeOverride = bridge,
    )

    private fun session(channels: Int = 1, durationMs: Long = 0L) = RecordedSession(
        id = "session-test",
        wavFile = wavFile,
        input = CaptureInput.MICROPHONE_MONO,
        sampleRate = sampleRate,
        channels = channels,
        startedAt = 0L,
        durationMs = durationMs,
        sessionLabel = null,
    )

    private fun speech(seconds: Int): FloatArray =
        FloatArray(seconds * sampleRate) { 0.25f * sin(2.0 * PI * 440.0 * it / sampleRate).toFloat() }

    private fun writeMonoWav(pcm: FloatArray) {
        val shorts = ShortArray(pcm.size) { (pcm[it] * 32767f).toInt().toShort() }
        WavWriter(wavFile, channels = 1, sampleRate = sampleRate).apply {
            open(); write(shorts); close()
        }
    }

    private fun writeStereoWav(seconds: Int) {
        val frames = seconds * sampleRate
        val interleaved = ShortArray(frames * 2)
        for (f in 0 until frames) {
            val s = (0.25f * sin(2.0 * PI * 440.0 * f / sampleRate) * 32767f).toInt().toShort()
            interleaved[f * 2] = s
            interleaved[f * 2 + 1] = s
        }
        WavWriter(wavFile, channels = 2, sampleRate = sampleRate).apply {
            open(); write(interleaved); close()
        }
    }
}
