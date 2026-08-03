package com.justsaid.app.stt

/**
 * Tuning knobs for the STT pass. Defaults implement Constitution N5: ~30 s windows
 * with ~1 s overlap so native buffers stay a fixed size and long calls cannot grow
 * per-chunk allocations.
 */
data class WhisperParams(
    val sampleRate: Int = SAMPLE_RATE_HZ,
    val chunkSeconds: Int = 30,
    val overlapSeconds: Int = 1,
    val translate: Boolean = false,
) {
    init {
        require(overlapSeconds < chunkSeconds) { "overlap must be smaller than the chunk" }
    }

    val chunkSamples: Int get() = chunkSeconds * sampleRate
    val overlapSamples: Int get() = overlapSeconds * sampleRate

    /** Samples the window start advances by: chunk minus overlap. */
    val strideSamples: Int get() = chunkSamples - overlapSamples

    companion object {
        const val SAMPLE_RATE_HZ = 16_000

        /**
         * Hard cap until Phase 4 chunked summarization lands. Sessions longer than
         * this fail with a user-safe message instead of a silent partial transcript.
         */
        const val MAX_SESSION_DURATION_MS = 15 * 60 * 1000L

        /**
         * Physical big-core estimate for the native thread pool (Constitution N4).
         * `availableProcessors` counts logical cores including LITTLE ones, so halve
         * it and clamp; 4 is the documented default when the topology is unknown.
         */
        fun threadCount(
            logicalCores: Int = Runtime.getRuntime().availableProcessors(),
        ): Int = if (logicalCores <= 0) 4 else (logicalCores / 2).coerceIn(2, 6)
    }
}
