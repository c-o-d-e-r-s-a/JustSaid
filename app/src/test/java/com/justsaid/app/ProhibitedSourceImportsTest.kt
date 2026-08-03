package com.justsaid.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Migration task 1 acceptance: production Kotlin must not import or reference
 * dialer-only platform APIs or prohibited microphone sources.
 */
class ProhibitedSourceImportsTest {

  private val prohibitedTokens = listOf(
    "android.telecom",
    "AccessibilityService",
    "VOICE_CALL",
    "VOICE_RECOGNITION",
    "VOICE_COMMUNICATION",
  )

  @Test
  fun `main sources contain no prohibited integration references`() {
    val root = File(checkNotNull(System.getProperty("user.dir"))).resolve("src/main/java")
    val offenders = mutableListOf<String>()
    root.walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .forEach { file ->
        val text = file.readText()
        prohibitedTokens.forEach { token ->
          if (text.contains(token)) {
            offenders += "${file.relativeTo(root)}: $token"
          }
        }
      }
    assertThat(offenders).isEmpty()
  }
}
