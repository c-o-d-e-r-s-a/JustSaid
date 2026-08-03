package com.justsaid.app.audio

import java.io.File

/** Input contract for companion capture — microphone mono only in this product. */
enum class CaptureInput { MICROPHONE_MONO }

/**
 * Finished private recording handed to STT/pipeline after an explicit user stop.
 * The [wavFile] is temporary and must be deleted by the pipeline (Constitution A1).
 */
data class RecordedSession(
    val id: String,
    val wavFile: File,
    val input: CaptureInput,
    val sampleRate: Int,
    val channels: Int,
    val startedAt: Long,
    val durationMs: Long,
    val sessionLabel: String?,
)
