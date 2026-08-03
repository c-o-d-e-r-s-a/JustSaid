package com.justsaid.app.session

import java.io.File

/** Verified deletion for companion-session temporary WAV buffers. */
internal object SessionWavFiles {
    fun deleteVerified(file: File): Boolean =
        !file.exists() || (file.delete() && !file.exists())
}
