package com.justsaid.app.session

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StaleAudioCleanerTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private lateinit var cacheDir: File
  private lateinit var unrelatedDir: File

  @Before
  fun setUp() {
    cacheDir = tmp.newFolder("cache")
    unrelatedDir = tmp.newFolder("other")
  }

  @After
  fun tearDown() {
    cacheDir.listFiles()?.forEach { it.delete() }
    unrelatedDir.listFiles()?.forEach { it.delete() }
  }

  @Test
  fun `deletes only matching session wav files in cache dir`() {
    val stale = File(cacheDir, "${StaleAudioCleaner.SESSION_WAV_PREFIX}abc.wav").apply {
      writeText("audio")
    }
    val otherWav = File(cacheDir, "export_summary.wav").apply { writeText("export") }
    val outside = File(unrelatedDir, "${StaleAudioCleaner.SESSION_WAV_PREFIX}x.wav").apply {
      writeText("outside")
    }

    val result = cleaner(cacheDir).clean()

    assertThat(result.removedCount).isEqualTo(1)
    assertThat(result.isClean).isTrue()
    assertThat(stale.exists()).isFalse()
    assertThat(otherWav.exists()).isTrue()
    assertThat(outside.exists()).isTrue()
  }

  @Test
  fun `returns clean result when cache dir is empty`() {
    val result = cleaner(cacheDir).clean()
    assertThat(result.removedCount).isEqualTo(0)
    assertThat(result.isClean).isTrue()
  }

  @Test
  fun `stale cleanup failure retains undeleted session wav`() {
    val stale = File(cacheDir, "${StaleAudioCleaner.SESSION_WAV_PREFIX}locked.wav").apply {
      writeBytes(byteArrayOf(1))
    }
    val lock = java.io.RandomAccessFile(stale, "rw")
    try {
      val result = cleaner(cacheDir).clean()
      assertThat(result.isClean).isFalse()
      assertThat(result.failedFiles).containsExactly(stale)
      assertThat(stale.exists()).isTrue()
    } finally {
      lock.close()
      stale.delete()
    }
  }

  private fun cleaner(dir: File): StaleAudioCleaner =
    StaleAudioCleaner(context = FakeContext(dir))
}
