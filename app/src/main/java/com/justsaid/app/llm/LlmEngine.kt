package com.justsaid.app.llm

import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.ModelPaths
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin wrapper for the llama.cpp summarizer model. One [generate] call = one
 * full model+context lifecycle: init once, run the whole generation against that
 * context, free in `finally` (Constitution N1/N3) — a 3B model is far too large
 * to keep resident between calls.
 *
 * The three `external fun`s here are the only JNI entry points; their mangled
 * symbols live in `cpp/justsaid_llm_jni.cpp` (AGENTS.md §3, llama symbols only).
 * All orchestration goes through [NativeLlmBridge] so JVM tests inject a fake and
 * never load the `.so` — which is also why the library loads lazily on first real
 * use instead of in a `companion init`.
 */
@Singleton
class LlmEngine(
    private val modelPaths: ModelPaths,
    private val dispatcher: CoroutineDispatcher,
    private val params: LlmParams,
    bridgeOverride: NativeLlmBridge?,
) {

    @Inject
    constructor(
        modelPaths: ModelPaths,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(modelPaths, dispatcher, LlmParams(), null)

    private val bridge: NativeLlmBridge = bridgeOverride ?: RealBridge()

    // ── JNI surface (do not rename: symbols are mangled against this class) ──
    external fun nativeInit(modelPath: String, threads: Int, nGpuLayers: Int): Long
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, temp: Float): String
    external fun nativeFree(handle: Long)

    private inner class RealBridge : NativeLlmBridge {
        override fun init(modelPath: String, threads: Int, nGpuLayers: Int): Long {
            loadNativeLibrary()
            return nativeInit(modelPath, threads, nGpuLayers)
        }

        override fun generate(handle: Long, prompt: String, maxTokens: Int, temp: Float): String =
            nativeGenerate(handle, prompt, maxTokens, temp)

        override fun free(handle: Long) = nativeFree(handle)
    }

    /**
     * Runs one complete generation for the fully assembled [prompt] (chat template
     * included — see [Prompts]). Runs on the injected default dispatcher, never the
     * main thread (Constitution N4). An empty model output is a failure: the prompt
     * mandates the `NONE` token when there is nothing to say, so blank means the
     * native layer errored (bad handle, prompt overflow, decode failure).
     */
    suspend fun generate(prompt: String): JustSaidResult<String> =
        withContext(dispatcher) {
            val modelFile = modelPaths.llmModelFile()
            if (!modelFile.exists()) {
                return@withContext JustSaidResult.Failure("LLM model missing: ${modelFile.name}")
            }

            val handle = bridge.init(
                modelFile.absolutePath,
                LlmParams.threadCount(),
                params.gpuOffloadLayers,
            )
            if (handle == 0L) {
                return@withContext JustSaidResult.Failure("llm context init failed")
            }

            try {
                val output = bridge.generate(handle, prompt, params.maxTokens, params.temperature)
                if (output.isBlank()) {
                    JustSaidResult.Failure("llm generation produced no output")
                } else {
                    JustSaidResult.Success(output)
                }
            } catch (e: Exception) {
                JustSaidResult.Failure("llm generation failed", e)
            } finally {
                bridge.free(handle)
            }
        }

    private companion object {
        @Volatile
        private var libraryLoaded = false

        fun loadNativeLibrary() {
            if (!libraryLoaded) {
                synchronized(this) {
                    if (!libraryLoaded) {
                        System.loadLibrary("justsaid_native")
                        libraryLoaded = true
                    }
                }
            }
        }
    }
}
