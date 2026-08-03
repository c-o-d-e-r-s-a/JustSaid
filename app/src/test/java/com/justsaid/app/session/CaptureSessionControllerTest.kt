package com.justsaid.app.session

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.audio.AudioSource
import com.justsaid.app.audio.AudioSourceFactory
import com.justsaid.app.audio.CaptureInput
import com.justsaid.app.audio.FileAudioSource
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.audio.SessionPipeline
import com.justsaid.app.audio.WavFileProvider
import com.justsaid.app.audio.WavWriter
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureSessionControllerTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private val dispatcher = UnconfinedTestDispatcher()
  private val pipeline = mockk<SessionPipeline>()
  private val staleCleaner = mockk<StaleAudioCleaner>(relaxed = true)
  private val gateway = object : CaptureServiceGateway {
    override fun start(): Boolean = true
    override fun shutdown() = Unit
  }
  private lateinit var fixture: File
  private lateinit var outputDir: File
  private var pipelineCalls = 0
  private var lastSession: RecordedSession? = null

  @Before
  fun setUp() {
    fixture = tmp.newFile("fixture.wav")
    writeMonoFixture(fixture, samples = shortArrayOf(100, 200, 300, 400))
    outputDir = tmp.newFolder("wav_out")
    pipelineCalls = 0
    lastSession = null
    coEvery { pipeline.process(any()) } coAnswers {
      pipelineCalls++
      val session = firstArg<RecordedSession>()
      lastSession = session
      session.wavFile.delete()
      JustSaidResult.Success(dummySummary(session))
    }
  }

  @After
  fun tearDown() {
    outputDir.listFiles()?.forEach { it.delete() }
  }

  @Test
  fun `start to stop produces RecordedSession via fake source`() = runTest(dispatcher) {
    val controller = controller()

    controller.start(sessionLabel = "meeting")
    dispatcher.scheduler.advanceUntilIdle()
    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Recording::class.java)

    controller.stop()
    awaitTerminalState(controller)

    assertThat(pipelineCalls).isEqualTo(1)
    val session = lastSession!!
    assertThat(session.input).isEqualTo(CaptureInput.MICROPHONE_MONO)
    assertThat(session.channels).isEqualTo(1)
    assertThat(session.sampleRate).isEqualTo(AudioSource.SAMPLE_RATE)
    assertThat(session.sessionLabel).isEqualTo("meeting")
    assertThat(session.wavFile.exists()).isFalse()
    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Completed::class.java)
  }

  @Test
  fun `double start is ignored while recording`() = runTest(dispatcher) {
    val controller = controller()
    controller.start()
    dispatcher.scheduler.advanceUntilIdle()
    val first = controller.state.value as CaptureSessionState.Recording
    controller.start()
    val second = controller.state.value as CaptureSessionState.Recording
    assertThat(second.sessionId).isEqualTo(first.sessionId)
    controller.stop()
    awaitTerminalState(controller)
    assertThat(pipelineCalls).isEqualTo(1)
  }

  @Test
  fun `double stop launches pipeline once`() = runTest(dispatcher) {
    val controller = controller()
    controller.start()
    dispatcher.scheduler.advanceUntilIdle()
    controller.stop()
    controller.stop()
    awaitTerminalState(controller)
    assertThat(pipelineCalls).isEqualTo(1)
  }

  @Test
  fun `stop while idle is ignored`() = runTest(dispatcher) {
    val controller = controller()
    controller.stop()
    assertThat(controller.state.value).isEqualTo(CaptureSessionState.Idle)
    assertThat(pipelineCalls).isEqualTo(0)
  }

  @Test
  fun `writer open failure deletes temp file`() = runTest(dispatcher) {
    val failingGateway = object : CaptureServiceGateway {
      override fun start(): Boolean = false
      override fun shutdown() = Unit
    }
    val controller = controller(captureGateway = failingGateway)
    controller.start()
    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
    assertThat(outputDir.listFiles()?.count { it.name.startsWith("justsaid_session_") }).isEqualTo(0)
    assertThat(pipelineCalls).isEqualTo(0)
  }

  @Test
  fun `empty capture deletes wav and fails without pipeline`() = runTest(dispatcher) {
    val emptyFixture = tmp.newFile("empty.wav")
    writeMonoFixture(emptyFixture, samples = shortArrayOf())
    val controller = controller(fixtureFile = emptyFixture)
    controller.start()
    dispatcher.scheduler.advanceUntilIdle()
    controller.stop()
    awaitTerminalState(controller)
    assertThat(pipelineCalls).isEqualTo(0)
    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
  }

  private fun controller(
    fixtureFile: File = fixture,
    wavProvider: WavFileProvider = WavFileProvider {
      File.createTempFile("justsaid_session_", ".wav", outputDir)
    },
    captureGateway: CaptureServiceGateway = gateway,
  ): CaptureSessionController {
    val factory = AudioSourceFactory { FileAudioSource(fixtureFile) }
    return CaptureSessionController(
      audioSourceFactory = factory,
      wavFileProvider = wavProvider,
      sessionPipeline = pipeline,
      staleAudioCleaner = staleCleaner,
      captureServiceGateway = captureGateway,
      dispatcher = dispatcher,
    )
  }

  private suspend fun awaitTerminalState(controller: CaptureSessionController) {
    while (
      controller.state.value !is CaptureSessionState.Completed &&
      controller.state.value !is CaptureSessionState.Failed
    ) {
      dispatcher.scheduler.advanceUntilIdle()
    }
  }

  private fun dummySummary(session: RecordedSession) = CallSummary(
    id = 0L,
    contactName = session.sessionLabel,
    phoneNumber = session.id,
    createdAt = session.startedAt,
    items = emptyList(),
    fullTranscript = "",
  )

  private fun writeMonoFixture(file: File, samples: ShortArray) {
    WavWriter(file, channels = 1).apply {
      open()
      if (samples.isNotEmpty()) write(samples)
      close()
    }
  }
}
