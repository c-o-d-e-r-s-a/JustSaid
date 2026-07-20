package com.justsaid.app.audio

import java.io.File

/**
 * Which capture mechanism actually succeeded at runtime. Only [STEREO] provides
 * trustworthy L=local / R=remote channel separation; the mono tiers force the
 * downstream pipeline to mark speakers UNKNOWN (Constitution S2).
 */
enum class CaptureTier { STEREO, DUAL_MONO, MIC_ONLY }

/**
 * The finished, private call recording handed to the pipeline (Phase 3/4).
 *
 * The [wavFile] lives in `cacheDir` and is the pipeline's responsibility to delete
 * the instant summarization completes — success OR failure (Constitution A1).
 * Phase 2 never persists this file anywhere else.
 */
data class RecordedCall(
    val wavFile: File,
    val tier: CaptureTier,
    val sampleRate: Int,
    val channels: Int,
    val phoneNumber: String,
    val contactName: String?,
    val durationMs: Long,
)
