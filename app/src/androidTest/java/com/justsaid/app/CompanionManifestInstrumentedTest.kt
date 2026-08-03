package com.justsaid.app

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Migration task 1 (device): merged manifest must not request dialer/telecom/accessibility
 * permissions or register prohibited platform integrations.
 */
@RunWith(AndroidJUnit4::class)
class CompanionManifestInstrumentedTest {

  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test
  fun mergedManifest_doesNotRequestProhibitedPermissions() {
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
      "android.permission.BIND_INCALL_SERVICE",
      "android.permission.BIND_ACCESSIBILITY_SERVICE",
      "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
    )

    assertThat(permissions).containsNoneIn(prohibited)
  }

  @Test
  fun mergedManifest_requestsOnlyCompanionPermissions() {
    val permissions = context.packageManager
      .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
      .requestedPermissions
      ?.toSet()
      ?: emptySet()

    assertThat(permissions).containsExactly(
      "android.permission.INTERNET",
      "android.permission.RECORD_AUDIO",
      "android.permission.POST_NOTIFICATIONS",
      "android.permission.FOREGROUND_SERVICE",
      "android.permission.FOREGROUND_SERVICE_MICROPHONE",
    )
  }

  @Test
  fun mergedManifest_hasNoTelecomOrAccessibilityServices() {
    val services = context.packageManager
      .getPackageInfo(context.packageName, PackageManager.GET_SERVICES)
      .services
      ?.map { it.name }
      ?: emptyList()

    val prohibited = listOf(
      "JustSaidInCallService",
      "CaptureUnlockAccessibilityService",
      "android.telecom.InCallService",
      "AccessibilityService",
    )
    prohibited.forEach { token ->
      assertThat(services.joinToString()).doesNotContain(token)
    }
  }

  @Test
  fun sourceManifest_excludesDialerTelecomAndAccessibilityDeclarations() {
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
      "READ_PHONE_STATE",
      "READ_CALL_LOG",
      "READ_CONTACTS",
      "ANSWER_PHONE_CALLS",
    )
    prohibited.forEach { token ->
      assertThat(manifest).doesNotContain(token)
    }
  }

  private fun manifestSourceText(): String {
    val moduleRoot = File(checkNotNull(System.getProperty("user.dir")))
    val manifest = moduleRoot.resolve("src/main/AndroidManifest.xml")
    check(manifest.isFile) { "Expected manifest at $manifest" }
    return manifest.readText()
  }
}
