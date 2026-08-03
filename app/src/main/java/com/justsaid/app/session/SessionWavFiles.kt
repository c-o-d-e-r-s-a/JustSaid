package com.justsaid.app.session

import java.io.File

/** Verified deletion for companion-session temporary WAV buffers. */
internal object SessionWavFiles {
    const val TEMP_AUDIO_DELETION_FAILED: String = "Temporary audio could not be deleted."

    fun deleteVerified(file: File): Boolean =
        !file.exists() || (file.delete() && !file.exists())
}
