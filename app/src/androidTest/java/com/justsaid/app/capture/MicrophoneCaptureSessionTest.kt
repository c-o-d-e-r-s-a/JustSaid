package com.justsaid.app.capture

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.justsaid.app.service.MicrophoneCaptureService
import com.justsaid.app.session.CaptureSessionController
import com.justsaid.app.session.CaptureSessionState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import java.io.File

/**
 * Instrumented: explicit start runs the microphone FGS with granted permission;
 * explicit stop ends capture and tears down the service.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MicrophoneCaptureSessionTest {

  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val permissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

  private lateinit var context: Context

  @Inject lateinit var controller: CaptureSessionController

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    TestCaptureModule.fixtureWavFile = copyFixture(context)
    hiltRule.inject()
  }

  @Test
  fun userStartedCaptureStartsFgsAndStopsOnExplicitStop() = runBlocking {
    controller.start()
    withTimeout(5_000) {
      while (controller.state.value !is CaptureSessionState.Recording) {
        delay(25)
      }
    }
    assertThat(isServiceRunning(MicrophoneCaptureService::class.java)).isTrue()

    controller.stop()
    withTimeout(5_000) {
      while (
        controller.state.value !is CaptureSessionState.Completed &&
        controller.state.value !is CaptureSessionState.Failed
      ) {
        delay(25)
      }
    }
    assertThat(isServiceRunning(MicrophoneCaptureService::class.java)).isFalse()
  }

  private fun isServiceRunning(serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Int.MAX_VALUE)
      .any { it.service.className == serviceClass.name }
  }

  private fun copyFixture(context: Context): File {
    val out = File(context.cacheDir, "capture_test_fixture.wav")
    context.assets.open("fixtures/jfk.wav").use { input ->
      out.outputStream().use { output -> input.copyTo(output) }
    }
    return out
  }
}
