package com.justsaid.app.session

import java.io.File

/** Outcome of a synchronous stale temporary-WAV sweep. */
data class StaleCleanupResult(
    val removedCount: Int,
    val failedFiles: List<File>,
) {
    val isClean: Boolean get() = failedFiles.isEmpty()
}
