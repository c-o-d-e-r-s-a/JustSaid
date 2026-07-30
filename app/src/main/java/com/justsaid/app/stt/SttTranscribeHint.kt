package com.justsaid.app.stt

import com.justsaid.app.data.repo.SttLanguageLock

/**
 * Maps user settings to whisper's language hint plus an optional allow-list for
 * constrained auto-detect (comma-separated ISO 639-1 codes passed to JNI).
 */
object SttTranscribeHint {

    /**
     * @return whisper language param, then allowed-language CSV (empty = no restriction).
     */
    fun resolve(lock: SttLanguageLock, spokenLanguageCodes: Set<String>): Pair<String, String> {
        when (lock) {
            SttLanguageLock.EN -> return "en" to ""
            SttLanguageLock.AUTO -> {
                val codes = spokenLanguageCodes
                    .map { it.trim().lowercase() }
                    .filter { it in WhisperLanguageCatalog.supportedCodes }
                    .toSet()
                return when {
                    codes.isEmpty() -> "auto" to ""
                    codes.size == 1 -> codes.single() to ""
                    else -> "auto" to codes.sorted().joinToString(",")
                }
            }
        }
    }
}
