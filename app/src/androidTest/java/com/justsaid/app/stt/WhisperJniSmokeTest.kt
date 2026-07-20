package com.justsaid.app.stt

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.justsaid.app.audio.CaptureTier
import com.justsaid.app.audio.RecordedCall
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.ModelPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real-`.so` smoke test (TESTING.md C.2). The library-load and free-idempotence
 * checks always run; the transcription check needs a real whisper model on the
 * device and self-skips when absent (CI has no model — TESTING.md C.3).
 *
 * To provision a model for the full check (any `ggml-*.bin`; tiny-q5_1 at ~32 MB
 * is plenty):
 * ```
 * adb push ggml-tiny-q5_1.bin /data/local/tmp/
 * adb shell run-as com.justsaid.app.debug sh -c \
 *   "mkdir -p files/models && cp /data/local/tmp/ggml-tiny-q5_1.bin files/models/"
 * ```
 */
@RunWith(AndroidJUnit4::class)
class WhisperJniSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Any whisper GGML weights present in the app's private model dir. */
    private fun deviceModel(): File? =
        File(context.filesDir, "models").listFiles()
            ?.firstOrNull { it.name.startsWith("ggml-") && it.name.endsWith(".bin") }

    private fun engine(model: File?) = WhisperEngine(
        modelPaths = object : ModelPaths {
            override fun sttModelFile(): File = model ?: File(context.filesDir, "missing.bin")
            override fun llmModelFile(): File = sttModelFile()
            override fun modelsReady(): Boolean = model != null
        },
        audioDecoder = AudioDecoder(),
        vadGate = VadGate(),
        dispatcher = Dispatchers.Default,
        params = WhisperParams(),
        bridgeOverride = null, // real JNI
    )

    @Test
    fun nativeInit_withBogusPath_returnsZeroWithoutCrashing() {
        loadLibrary()
        val handle = engine(null).nativeInit("/nonexistent/model.bin", 2)
        assertThat(handle).isEqualTo(0L)
    }

    @Test
    fun nativeFree_isZeroSafeAndIdempotent() {
        loadLibrary()
        val e = engine(null)
        e.nativeFree(0L)          // zero-safe
        e.nativeFree(0xDEAD_BEEFL) // unknown handle — must not crash
        e.nativeFree(0xDEAD_BEEFL) // and stays safe on repeat
    }

    @Test
    fun transcribesFixture_andDoubleFreeIsSafe() {
        val model = deviceModel()
        assumeTrue("no whisper model on device; see KDoc to provision one", model != null)
        loadLibrary()

        val wav = copyAssetToCache("fixtures/jfk.wav")
        try {
            val e = engine(model)

            // Direct native lifecycle: init once, transcribe, free twice (N3).
            val handle = e.nativeInit(model!!.absolutePath, WhisperParams.threadCount())
            assertThat(handle).isNotEqualTo(0L)
            val pcm = AudioDecoder().decode(wav).mono
            val json = e.nativeTranscribe(handle, pcm, "en", false)
            assertThat(json).contains("\"segments\"")
            assertThat(json.length).isGreaterThan("{\"segments\":[]}".length)
            e.nativeFree(handle)
            e.nativeFree(handle) // idempotent double free

            // Full Kotlin path on the same fixture.
            val result = runBlocking {
                e.transcribe(
                    RecordedCall(
                        wavFile = wav,
                        tier = CaptureTier.MIC_ONLY,
                        sampleRate = 16_000,
                        channels = 1,
                        phoneNumber = "+15555550123",
                        contactName = null,
                        durationMs = 11_000L,
                    ),
                    language = "en",
                )
            }
            val transcript = (result as JustSaidResult.Success).value
            assertThat(transcript.segments).isNotEmpty()
            assertThat(transcript.plainText()).isNotEmpty()
        } finally {
            wav.delete() // test owns its temp fixture (Phase 3 never deletes call wavs)
        }
    }

    private fun copyAssetToCache(assetPath: String): File {
        val out = File(context.cacheDir, "smoke_${System.currentTimeMillis()}.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetPath).use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        return out
    }

    private companion object {
        fun loadLibrary() = System.loadLibrary("justsaid_native")
    }
}
