package com.justsaid.app.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.ModelPaths
import com.justsaid.app.core.Speaker
import com.justsaid.app.core.Transcript
import com.justsaid.app.core.TranscriptSegment
import com.justsaid.app.summary.HallucinationGuard
import com.justsaid.app.summary.PromiseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real-`.so` smoke test for the llama side of `libjustsaid_native.so`
 * (TESTING.md C.2). The library-load and free-idempotence checks always run; the
 * generation check needs a GGUF model on the device and self-skips when absent
 * (CI has no model — TESTING.md C.3).
 *
 * To provision the summarizer model for the full check:
 * ```
 * adb push llama-3.2-3b-instruct-q4_k_m.gguf /data/local/tmp/
 * adb shell run-as com.justsaid.app.debug sh -c \
 *   "mkdir -p files/models && cp /data/local/tmp/llama-3.2-3b-instruct-q4_k_m.gguf files/models/"
 * ```
 */
@RunWith(AndroidJUnit4::class)
class LlmJniSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Any GGUF weights present in the app's private model dir. */
    private fun deviceModel(): File? =
        File(context.filesDir, "models").listFiles()
            ?.firstOrNull { it.name.endsWith(".gguf") }

    private fun engine(model: File?) = LlmEngine(
        modelPaths = object : ModelPaths {
            override fun sttModelFile(): File = llmModelFile()
            override fun llmModelFile(): File = model ?: File(context.filesDir, "missing.gguf")
            override fun modelsReady(): Boolean = model != null
        },
        dispatcher = Dispatchers.Default,
        params = LlmParams(),
        bridgeOverride = null, // real JNI
    )

    @Test
    fun nativeInit_withBogusPath_returnsZeroWithoutCrashing() {
        loadLibrary()
        val handle = engine(null).nativeInit("/nonexistent/model.gguf", 2, 0)
        assertThat(handle).isEqualTo(0L)
    }

    @Test
    fun nativeFree_isZeroSafeAndIdempotent() {
        loadLibrary()
        val e = engine(null)
        e.nativeFree(0L)           // zero-safe
        e.nativeFree(0xBAD_F00DL)  // unknown handle — must not crash
        e.nativeFree(0xBAD_F00DL)  // and stays safe on repeat
    }

    @Test
    fun generatesFormatConformantOutput_andDoubleFreeIsSafe() {
        val model = deviceModel()
        assumeTrue("no GGUF model on device; see KDoc to provision one", model != null)
        loadLibrary()

        val e = engine(model)
        val transcript = Transcript(
            listOf(
                TranscriptSegment(Speaker.LOCAL, "Hi Grandma, I'll bring the groceries on Saturday.", 0, 3000),
                TranscriptSegment(Speaker.REMOTE, "Wonderful. Could you pick up two loaves of bread too?", 3200, 6500),
                TranscriptSegment(Speaker.LOCAL, "Sure, I'll pick up two loaves of bread.", 6700, 9000),
            ),
        )

        // Direct native lifecycle: init once, generate, free twice (N3).
        val handle = e.nativeInit(model!!.absolutePath, LlmParams.threadCount(), 0)
        assertThat(handle).isNotEqualTo(0L)
        val raw = e.nativeGenerate(
            handle,
            Prompts.forTranscript(transcript.plainText()),
            /*maxTokens=*/256,
            /*temp=*/0.1f,
        )
        e.nativeFree(handle)
        e.nativeFree(handle) // idempotent double free

        assertThat(raw).isNotEmpty()
        assertFormatConformant(raw, transcript)

        // Full Kotlin path (engine owns init/free) on the same transcript.
        val result = runBlocking { e.generate(Prompts.forTranscript(transcript.plainText())) }
        val output = (result as JustSaidResult.Success).value
        assertFormatConformant(output, transcript)
    }

    /**
     * Format conformance per the Phase 4 prompt contract: either the single NONE
     * token, or at least one parseable `Task/Item [Qty] (Proof: "...")` line — and
     * anything that parses must survive the same guardrail the app enforces.
     */
    private fun assertFormatConformant(output: String, transcript: Transcript) {
        if (output.trim().equals(Prompts.NONE_TOKEN, ignoreCase = true)) return
        val parsed = PromiseParser.parse(output)
        assertThat(parsed).isNotEmpty()
        assertThat(HallucinationGuard.validate(parsed, transcript)).isNotEmpty()
    }

    private companion object {
        fun loadLibrary() = System.loadLibrary("justsaid_native")
    }
}
