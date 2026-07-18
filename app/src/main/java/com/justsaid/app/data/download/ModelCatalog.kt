package com.justsaid.app.data.download

import com.justsaid.app.data.repo.SttLanguageLock

/**
 * A single downloadable model artifact. [sha256] and [sizeBytes] are verified
 * against the file after download (checksum gate) so a truncated or tampered
 * file is never accepted.
 */
data class ModelSpec(
    val id: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

/** One place to update Hugging Face hosting. See docs/01-onboarding-downloader.md. */
object HuggingFace {
    const val BASE: String = "https://huggingface.co/"
}

/**
 * Hardcoded registry of model weights. Values (URL, size, sha256) were verified
 * against the Hugging Face API. Note: the canonical whisper.cpp repo publishes the
 * base/small.en quantizations as `q5_1` (there is no `q5_0` for these sizes).
 */
object ModelCatalog {
    const val MODELS_DIR: String = "models"

    /** Default STT: multilingual, used when language lock is AUTO. */
    val STT_BASE_MULTILINGUAL = ModelSpec(
        id = "stt-base-q5_1",
        fileName = "ggml-base-q5_1.bin",
        url = HuggingFace.BASE + "ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
        sizeBytes = 59_707_625L,
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
    )

    /** Optional STT: english-only, smaller/faster, used when language lock is EN. */
    val STT_SMALL_EN = ModelSpec(
        id = "stt-small-en-q5_1",
        fileName = "ggml-small.en-q5_1.bin",
        url = HuggingFace.BASE + "ggerganov/whisper.cpp/resolve/main/ggml-small.en-q5_1.bin",
        sizeBytes = 190_098_681L,
        sha256 = "bfdff4894dcb76bbf647d56263ea2a96645423f1669176f4844a1bf8e478ad30",
    )

    /** Summarizer LLM. Repo is public (not gated); anonymous download works. */
    val LLM = ModelSpec(
        id = "llm-llama-3.2-3b-q4_k_m",
        fileName = "llama-3.2-3b-instruct-q4_k_m.gguf",
        url = HuggingFace.BASE +
            "hugging-quants/Llama-3.2-3B-Instruct-Q4_K_M-GGUF/resolve/main/llama-3.2-3b-instruct-q4_k_m.gguf",
        sizeBytes = 2_019_373_920L,
        sha256 = "c55a83bfb6396799337853ca69918a0b9bbb2917621078c34570bc17d20fd7a1",
    )

    /** The whisper model required for the given language setting. */
    fun sttFor(lock: SttLanguageLock): ModelSpec = when (lock) {
        SttLanguageLock.EN -> STT_SMALL_EN
        SttLanguageLock.AUTO -> STT_BASE_MULTILINGUAL
    }

    /** The set of models that must be present for the app to run, given the language setting. */
    fun requiredFor(lock: SttLanguageLock): List<ModelSpec> = listOf(sttFor(lock), LLM)
}
