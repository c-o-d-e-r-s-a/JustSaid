package com.justsaid.app.capture

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import com.justsaid.app.service.MicrophoneCaptureService
import com.justsaid.app.session.CaptureSessionController
import com.justsaid.app.session.CaptureSessionState
import com.justsaid.app.summary.SummaryEvents
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke: fake audio drives a full manual capture session through FGS
 * acknowledgement, frame write, fake STT/summary, pending unsaved handoff, and WAV deletion.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ManualSessionSmokeTest {

  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @get:Rule(order = 1)
  val permissionRule = GrantPermissionRule.grant(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.POST_NOTIFICATIONS,
  )

  private lateinit var context: Context

  @Inject lateinit var controller: CaptureSessionController
  @Inject lateinit var summaryEvents: SummaryEvents

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    TestCaptureModule.fixtureWavFile = copyFixture(context)
    TestCaptureModule.useEmptyAudioSource = false
    TestCaptureModule.pipelineOutcome = FakePipelineOutcome.Success
    TestCaptureModule.lastWavFile = null
    summaryEvents.clear()
    hiltRule.inject()
  }

  @Test
  fun completeManualSession_acknowledgesFgs_writesFrames_emitsPendingSummary_deletesWav() =
    runBlocking {
      controller.start(sessionLabel = "standup")

      awaitRecording()
      assertThat(isServiceRunning(MicrophoneCaptureService::class.java)).isTrue()
      awaitWavPayloadWritten()

      controller.stop()
      awaitTerminal()

      assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Completed::class.java)
      assertThat(summaryEvents.latest.value).isNotNull()
      assertThat(summaryEvents.latest.value!!.id).isEqualTo(0L)
      assertThat(summaryEvents.latest.value!!.sessionLabel).isEqualTo("standup")
      assertThat(TestCaptureModule.lastWavFile?.exists()).isFalse()
      assertThat(isServiceRunning(MicrophoneCaptureService::class.java)).isFalse()
    }

  @Test
  fun sttFailure_deletesWav_withoutPendingSummary() = runBlocking {
    TestCaptureModule.pipelineOutcome = FakePipelineOutcome.SttFailure

    controller.start()
    awaitRecording()
    awaitWavPayloadWritten()
    controller.stop()
    awaitTerminal()

    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
    assertThat((controller.state.value as CaptureSessionState.Failed).userMessage)
      .isEqualTo(TestCaptureModule.STT_FAILED)
    assertThat(summaryEvents.latest.value).isNull()
    assertThat(TestCaptureModule.lastWavFile?.exists()).isFalse()
  }

  @Test
  fun summaryFailure_deletesWav_withoutPendingSummary() = runBlocking {
    TestCaptureModule.pipelineOutcome = FakePipelineOutcome.SummaryFailure

    controller.start()
    awaitRecording()
    awaitWavPayloadWritten()
    controller.stop()
    awaitTerminal()

    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
    assertThat((controller.state.value as CaptureSessionState.Failed).userMessage)
      .isEqualTo(TestCaptureModule.SUMMARY_FAILED)
    assertThat(summaryEvents.latest.value).isNull()
    assertThat(TestCaptureModule.lastWavFile?.exists()).isFalse()
  }

  @Test
  fun processingThrow_deletesWav_withoutPendingSummary() = runBlocking {
    TestCaptureModule.pipelineOutcome = FakePipelineOutcome.ProcessingThrow

    controller.start()
    awaitRecording()
    awaitWavPayloadWritten()
    controller.stop()
    awaitTerminal()

    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
    assertThat(summaryEvents.latest.value).isNull()
    assertThat(TestCaptureModule.lastWavFile?.exists()).isFalse()
  }

  @Test
  fun noAudioFrames_failsBeforePipeline_withoutPendingSummary() = runBlocking {
    TestCaptureModule.useEmptyAudioSource = true

    controller.start()
    awaitRecording()
    controller.stop()
    awaitTerminal()

    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
    assertThat((controller.state.value as CaptureSessionState.Failed).userMessage)
      .contains("No audio")
    assertThat(summaryEvents.latest.value).isNull()
    assertThat(TestCaptureModule.lastWavFile?.exists()).isFalse()
  }

  private suspend fun awaitRecording() {
    withTimeout(5_000) {
      while (controller.state.value !is CaptureSessionState.Recording) {
        delay(25)
      }
    }
  }

  private suspend fun awaitWavPayloadWritten() {
    withTimeout(5_000) {
      while ((TestCaptureModule.lastWavFile?.length() ?: 0L) <= 44) {
        delay(25)
      }
    }
  }

  private suspend fun awaitTerminal() {
    withTimeout(10_000) {
      while (
        controller.state.value !is CaptureSessionState.Completed &&
        controller.state.value !is CaptureSessionState.Failed
      ) {
        delay(25)
      }
    }
  }

  private fun isServiceRunning(serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    return manager.getRunningServices(Int.MAX_VALUE)
      .any { it.service.className == serviceClass.name }
  }

  private fun copyFixture(context: Context): File {
    val out = File(context.cacheDir, "manual_session_fixture.wav")
    context.assets.open("fixtures/jfk.wav").use { input ->
      out.outputStream().use { output -> input.copyTo(output) }
    }
    return out
  }
}
