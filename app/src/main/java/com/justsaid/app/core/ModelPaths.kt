package com.justsaid.app.core

import java.io.File

/**
 * Resolves on-device model file locations and readiness. All paths live under
 * app-private storage (`filesDir/models/`); never external storage (Phase 1 doc,
 * "Storage target [CRITICAL]").
 *
 * Contract consumed by later phases (STT/LLM engines) — do not change signatures.
 */
interface ModelPaths {
    /** The whisper model matching the current STT language setting (AUTO -> base, EN -> small.en). */
    fun sttModelFile(): File

    /** The llama summarizer model. */
    fun llmModelFile(): File

    /** True when both the resolved STT model and the LLM model are present on disk. */
    fun modelsReady(): Boolean
}
