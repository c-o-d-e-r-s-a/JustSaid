package com.justsaid.app.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes leftover companion-session WAV buffers from [Context.cacheDir].
 * Runs synchronously on app launch and before each new capture start.
 */
@Singleton
class StaleAudioCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        /** Prefix for [com.justsaid.app.audio.WavFileProvider] session temps. */
        const val SESSION_WAV_PREFIX: String = "justsaid_session_"
    }

  /**
   * Removes known session WAV temps from [Context.cacheDir].
   *
   * @return files that could not be deleted; callers must not start a new capture while
   * [StaleCleanupResult.isClean] is false.
   */
  fun clean(): StaleCleanupResult {
    val dir = context.cacheDir
    if (!dir.isDirectory) return StaleCleanupResult(removedCount = 0, failedFiles = emptyList())
    val sessionWavs = dir.listFiles()?.filter { it.isSessionWav() }.orEmpty()
    var removed = 0
    val failed = mutableListOf<File>()
    for (file in sessionWavs) {
      if (SessionWavFiles.deleteVerified(file)) {
        removed++
      } else {
        failed.add(file)
      }
    }
    return StaleCleanupResult(removedCount = removed, failedFiles = failed)
  }

    private fun File.isSessionWav(): Boolean =
        isFile && name.startsWith(SESSION_WAV_PREFIX) && name.endsWith(".wav")
}
