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

    val removed = cleaner(cacheDir).clean()

    assertThat(removed).isEqualTo(1)
    assertThat(stale.exists()).isFalse()
    assertThat(otherWav.exists()).isTrue()
    assertThat(outside.exists()).isTrue()
  }

  @Test
  fun `returns zero when cache dir is empty`() {
    assertThat(cleaner(cacheDir).clean()).isEqualTo(0)
  }

  private fun cleaner(dir: File): StaleAudioCleaner =
    StaleAudioCleaner(context = FakeContext(dir))
}
