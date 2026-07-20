package com.justsaid.app.stt

import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Energy-based voice activity gate (Constitution N5). Windows whose RMS energy sits
 * below [rmsThreshold] are skipped entirely: silence wastes inference time and is
 * whisper's main hallucination trigger. Deliberately simple — a false "has speech"
 * only costs time, while whisper itself handles partial silence fine.
 */
class VadGate @Inject constructor() {

    private val rmsThreshold: Float = DEFAULT_RMS_THRESHOLD

    /** True when [pcm]`[offset, offset+length)` carries enough energy to be speech. */
    fun hasSpeech(pcm: FloatArray, offset: Int = 0, length: Int = pcm.size - offset): Boolean {
        if (length <= 0) return false
        var sumSquares = 0.0
        val end = offset + length
        for (i in offset until end) {
            val s = pcm[i]
            sumSquares += s.toDouble() * s
        }
        return sqrt(sumSquares / length) >= rmsThreshold
    }

    private companion object {
        /**
         * ~-46 dBFS. Telephony noise floors sit well below this; quiet speech at a
         * normal call volume sits well above.
         */
        const val DEFAULT_RMS_THRESHOLD = 0.005f
    }
}
