package com.justsaid.app.llm

/**
 * Tuning knobs for the summarizer generation pass. Defaults implement Constitution
 * G4 (temperature ≤ 0.2 — near-greedy keeps proof quotes verbatim) and the
 * hardware-aware init: attempt full GPU layer offload, which the native side
 * silently downgrades to CPU-only when the offloaded load fails or the Vulkan
 * backend is compiled out (`-DJUSTSAID_VULKAN=OFF`).
 */
data class LlmParams(
    // 256 is enough for a short promise list; 512 inflated peak decode buffers on
    // mid-range phones that already struggle to keep the 3B weights resident.
    val maxTokens: Int = 256,
    val temperature: Float = 0.1f,
    val gpuOffloadLayers: Int = ATTEMPT_FULL_OFFLOAD,
) {
    init {
        require(temperature <= 0.2f) { "summarizer temperature must be ≤ 0.2 (Constitution G4)" }
    }

    companion object {
        /**
         * More layers than any 3B model has, so "offload everything the backend can".
         * With the CPU-only build this is a no-op; when Vulkan lands it becomes a
         * real offload attempt with automatic CPU fallback in nativeInit.
         */
        const val ATTEMPT_FULL_OFFLOAD = 99

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
