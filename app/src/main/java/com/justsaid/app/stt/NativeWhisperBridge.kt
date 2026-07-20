package com.justsaid.app.stt

/**
 * Seam between [WhisperEngine]'s pure-Kotlin orchestration (chunking, VAD, overlap
 * dedupe, speaker tagging) and the three JNI entry points. JVM unit tests inject a
 * fake returning canned JSON so no `.so` or device is needed (TESTING.md C.1).
 *
 * The production implementation lives inside [WhisperEngine] because the JNI symbol
 * names are mangled against that class (AGENTS.md §3.2).
 */
interface NativeWhisperBridge {

    /** Loads the model once; returns an opaque handle, or 0 on failure. */
    fun init(modelPath: String, threads: Int): Long

    /**
     * Runs whisper_full on one 16 kHz mono float window using the context behind
     * [handle]. Returns a JSON string `{"segments":[{"t0":ms,"t1":ms,"text":".."}]}`,
     * or an empty string on error.
     */
    fun transcribe(handle: Long, pcm: FloatArray, lang: String, translate: Boolean): String

    /** Frees the native context. Zero-safe and idempotent (Constitution N3). */
    fun free(handle: Long)
}
