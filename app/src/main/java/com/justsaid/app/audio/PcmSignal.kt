package com.justsaid.app.audio

/** Returns true if any sample in [frame] is non-zero (used to sanity-check capture). */
internal fun frameHasLiveSignal(frame: ShortArray, count: Int = frame.size): Boolean {
    for (i in 0 until count) {
        if (frame[i] != 0.toShort()) return true
    }
    return false
}

/** Peak absolute amplitude in a PCM frame (for debug logging only). */
internal fun framePeakAmplitude(frame: ShortArray, count: Int = frame.size): Int {
    var peak = 0
    for (i in 0 until count) {
        val abs = kotlin.math.abs(frame[i].toInt())
        if (abs > peak) peak = abs
    }
    return peak
}
