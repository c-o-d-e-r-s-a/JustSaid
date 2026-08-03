package com.justsaid.app

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migration task 1: the companion manifest must declare only microphone-capture
 * permissions and must not register dialer, telecom, or accessibility components.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CompanionManifestTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test
  fun `requested permissions include companion policy`() {
    val permissions = context.packageManager
      .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
      .requestedPermissions
      ?.toSet()
      ?: emptySet()

    assertThat(permissions).containsAtLeast(
      "android.permission.INTERNET",
      "android.permission.RECORD_AUDIO",
      "android.permission.POST_NOTIFICATIONS",
      "android.permission.FOREGROUND_SERVICE",
      "android.permission.FOREGROUND_SERVICE_MICROPHONE",
    )
  }

  @Test
  fun `manifest does not declare prohibited permissions`() {
    val permissions = context.packageManager
      .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
      .requestedPermissions
      ?.toSet()
      ?: emptySet()

  val prohibited = setOf(
      "android.permission.READ_PHONE_STATE",
      "android.permission.READ_CALL_LOG",
      "android.permission.READ_CONTACTS",
      "android.permission.ANSWER_PHONE_CALLS",
      "android.permission.MANAGE_OWN_CALLS",
      "android.permission.USE_FULL_SCREEN_INTENT",
      "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
    )

    assertThat(permissions).containsNoneIn(prohibited)
  }

  @Test
  fun `source manifest excludes dialer telecom and accessibility services`() {
    val manifest = manifestSourceText()
    val prohibited = listOf(
      "JustSaidInCallService",
      "CaptureForegroundService",
      "CaptureUnlockAccessibilityService",
      "android.telecom.InCallService",
      "BIND_INCALL_SERVICE",
      "BIND_ACCESSIBILITY_SERVICE",
      "android.intent.action.DIAL",
      "foregroundServiceType=\"phoneCall",
      "FOREGROUND_SERVICE_PHONE_CALL",
    )
    prohibited.forEach { token ->
      assertThat(manifest).doesNotContain(token)
    }
  }

  @Test
  fun `source manifest declares only companion permissions`() {
    val manifest = manifestSourceText()
    val declared = Regex("""<uses-permission android:name="([^"]+)"""")
      .findAll(manifest)
      .map { it.groupValues[1] }
      .toSet()
    assertThat(declared).containsExactly(
      "android.permission.INTERNET",
      "android.permission.RECORD_AUDIO",
      "android.permission.POST_NOTIFICATIONS",
      "android.permission.FOREGROUND_SERVICE",
      "android.permission.FOREGROUND_SERVICE_MICROPHONE",
    )
  }

  @Test
  fun `launcher activity has no dial intent filters`() {
    val manifest = manifestSourceText()
    assertThat(manifest).doesNotContain("android.intent.action.DIAL")
  }

  private fun manifestSourceText(): String {
    val moduleRoot = File(checkNotNull(System.getProperty("user.dir")))
    val manifest = moduleRoot.resolve("src/main/AndroidManifest.xml")
    check(manifest.isFile) { "Expected manifest at $manifest" }
    return manifest.readText()
  }
}
