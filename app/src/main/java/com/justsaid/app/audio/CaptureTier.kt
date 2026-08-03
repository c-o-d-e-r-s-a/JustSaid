package com.justsaid.app.audio

/**
 * Which capture mechanism actually succeeded at runtime. Companion capture is
 * [MIC_ONLY]; every transcript segment is tagged [com.justsaid.app.core.Speaker.UNKNOWN].
 */
enum class CaptureTier { STEREO, DUAL_MONO, MIC_ONLY }
