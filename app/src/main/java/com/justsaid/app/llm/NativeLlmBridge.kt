package com.justsaid.app.llm

/**
 * Seam between [LlmEngine]'s pure-Kotlin orchestration (prompt assembly, result
 * mapping) and the three llama JNI entry points. JVM unit tests inject a fake
 * returning canned model output so no `.so` or device is needed (TESTING.md C.1).
 *
 * The production implementation lives inside [LlmEngine] because the JNI symbol
 * names are mangled against that class (AGENTS.md §3.2) — same pattern as
 * [com.justsaid.app.stt.NativeWhisperBridge].
 */
interface NativeLlmBridge {

    /**
     * Loads the GGUF model + context once; returns an opaque handle, or 0 on failure.
     * [nGpuLayers] is the attempted Vulkan/NNAPI layer offload; the native side
     * falls back to CPU-only automatically when the offloaded load fails.
     */
    fun init(modelPath: String, threads: Int, nGpuLayers: Int): Long

    /**
     * Runs one full generation against the context behind [handle]: prompt eval in
     * batches, then low-temperature sampling until EOS or [maxTokens]. Returns the
     * generated text, or an empty string on error.
     */
    fun generate(handle: Long, prompt: String, maxTokens: Int, temp: Float): String

    /** Frees the native context + model. Zero-safe and idempotent (Constitution N3). */
    fun free(handle: Long)
}
