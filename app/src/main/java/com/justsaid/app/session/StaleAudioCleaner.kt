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
   * @return number of session WAV files removed.
   */
  fun clean(): Int {
    val dir = context.cacheDir
    if (!dir.isDirectory) return 0
    return dir.listFiles()
      ?.count { file -> file.isSessionWav() && file.delete() }
      ?: 0
  }

  private fun File.isSessionWav(): Boolean =
    isFile && name.startsWith(SESSION_WAV_PREFIX) && name.endsWith(".wav")
}
