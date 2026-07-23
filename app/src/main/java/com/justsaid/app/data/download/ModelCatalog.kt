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
 *
 * The summarizer LLM is chosen by [DeviceRamTier]: mid/low-end phones get the 1B
 * weights so generation does not OOM-kill the process (observed on Galaxy A14,
 * ~3.6 GB RAM, with the 3B Q4 weights).
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

    /** Light summarizer for ≤4 GiB phones. Repo is public; anonymous download works. */
    val LLM_1B = ModelSpec(
        id = "llm-llama-3.2-1b-q4_k_m",
        fileName = "llama-3.2-1b-instruct-q4_k_m.gguf",
        url = HuggingFace.BASE +
            "hugging-quants/Llama-3.2-1B-Instruct-Q4_K_M-GGUF/resolve/main/llama-3.2-1b-instruct-q4_k_m.gguf",
        sizeBytes = 807_690_656L,
        sha256 = "1d0e9419ec4e12aef73ccf4ffd122703e94c48344a96bc7c5f0f2772c2152ce3",
    )

    /** Standard summarizer for phones with more than 4 GiB RAM. */
    val LLM_3B = ModelSpec(
        id = "llm-llama-3.2-3b-q4_k_m",
        fileName = "llama-3.2-3b-instruct-q4_k_m.gguf",
        url = HuggingFace.BASE +
            "hugging-quants/Llama-3.2-3B-Instruct-Q4_K_M-GGUF/resolve/main/llama-3.2-3b-instruct-q4_k_m.gguf",
        sizeBytes = 2_019_373_920L,
        sha256 = "c55a83bfb6396799337853ca69918a0b9bbb2917621078c34570bc17d20fd7a1",
    )

    /** Every LLM artifact we may have on disk — used to delete the unused sibling. */
    val ALL_LLMS: List<ModelSpec> = listOf(LLM_1B, LLM_3B)

    /** Summarizer weights for this phone's RAM tier. */
    fun llmFor(tier: DeviceRamTier): ModelSpec = when (tier) {
        DeviceRamTier.LIGHT -> LLM_1B
        DeviceRamTier.STANDARD -> LLM_3B
    }

    /** The whisper model required for the given language setting. */
    fun sttFor(lock: SttLanguageLock): ModelSpec = when (lock) {
        SttLanguageLock.EN -> STT_SMALL_EN
        SttLanguageLock.AUTO -> STT_BASE_MULTILINGUAL
    }

    /** Models that must be present for the app to run, given language + RAM tier. */
    fun requiredFor(lock: SttLanguageLock, ramTier: DeviceRamTier): List<ModelSpec> =
        listOf(sttFor(lock), llmFor(ramTier))
}
