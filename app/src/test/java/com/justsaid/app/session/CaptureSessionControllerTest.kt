package com.justsaid.app.session

import com.google.common.truth.Truth.assertThat
import com.justsaid.app.audio.AudioSource
import com.justsaid.app.audio.AudioSourceFactory
import com.justsaid.app.audio.CaptureInput
import com.justsaid.app.audio.CaptureTier
import com.justsaid.app.audio.FileAudioSource
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.audio.SessionPipeline
import com.justsaid.app.audio.WavFileProvider
import com.justsaid.app.audio.WavWriter
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
  private lateinit var fixture: File
  private lateinit var outputDir: File
  private var pipelineCalls = 0
  private var gatewayStarts = 0
  private var gatewayShutdowns = 0
  private var lastSession: RecordedSession? = null

  private val gateway = object : CaptureServiceGateway {
    var startResult: Boolean = true
    override suspend fun start(): Boolean {
      gatewayStarts++
      return startResult
    }
    override fun shutdown() {
      gatewayShutdowns++
    }
  }

  @Before
  fun setUp() {
    fixture = tmp.newFile("fixture.wav")
    writeMonoFixture(fixture, samples = shortArrayOf(100, 200, 300, 400))
    outputDir = tmp.newFolder("wav_out")
    pipelineCalls = 0
    gatewayStarts = 0
    gatewayShutdowns = 0
    gateway.startResult = true
    lastSession = null
    every { staleCleaner.clean() } returns StaleCleanupResult(removedCount = 0, failedFiles = emptyList())
    coEvery { pipeline.process(any()) } coAnswers {
      pipelineCalls++
      val session = firstArg<RecordedSession>()
      lastSession = session
      SessionWavFiles.deleteVerified(session.wavFile)
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
    assertThat(gatewayStarts).isEqualTo(1)
    assertThat(gatewayShutdowns).isEqualTo(1)
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
    assertThat(gatewayStarts).isEqualTo(1)
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
    assertThat(gatewayShutdowns).isEqualTo(0)
  }

  @Test
  fun `foreground start rejection deletes temp file and skips pipeline`() = runTest(dispatcher) {
    gateway.startResult = false
    val controller = controller()
    controller.start()
    dispatcher.scheduler.advanceUntilIdle()
    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
    assertThat(outputDir.listFiles()?.count { it.name.startsWith("justsaid_session_") }).isEqualTo(0)
    assertThat(pipelineCalls).isEqualTo(0)
    assertThat(gatewayShutdowns).isEqualTo(0)
  }

  @Test
  fun `writer open failure shuts down service and deletes temp file`() = runTest(dispatcher) {
    val parentFile = tmp.newFile("not_a_directory")
    val controller = controller(
      wavProvider = WavFileProvider { File(parentFile, "justsaid_session_x.wav") },
    )
    controller.start()
    dispatcher.scheduler.advanceUntilIdle()
    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
    assertThat(pipelineCalls).isEqualTo(0)
    assertThat(gatewayShutdowns).isEqualTo(1)
  }

  @Test
  fun `source failure transitions to failed stops service and deletes wav`() = runTest(dispatcher) {
    val factory = AudioSourceFactory { ThrowingAudioSource() }
    val controller = controller(factory = factory)
    controller.start()
    awaitTerminalState(controller)
    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
    assertThat(pipelineCalls).isEqualTo(0)
    assertThat(gatewayShutdowns).isEqualTo(1)
    assertThat(outputDir.listFiles()?.count { it.name.startsWith("justsaid_session_") }).isEqualTo(0)
  }

  @Test
  fun `pipeline exception transitions to failed without escaping`() = runTest(dispatcher) {
    coEvery { pipeline.process(any()) } coAnswers {
      pipelineCalls++
      throw IllegalStateException("pipeline blew up")
    }
    val controller = controller()
    controller.start()
    dispatcher.scheduler.advanceUntilIdle()
    controller.stop()
    awaitTerminalState(controller)
    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
    assertThat((controller.state.value as CaptureSessionState.Failed).userMessage)
      .isEqualTo(CaptureSessionController.PROCESSING_FAILED)
    assertThat(pipelineCalls).isEqualTo(1)
  }

  @Test
  fun `pipeline deletion failure surfaces failed state`() = runTest(dispatcher) {
    coEvery { pipeline.process(any()) } coAnswers {
      pipelineCalls++
      val session = firstArg<RecordedSession>()
      lastSession = session
      JustSaidResult.Failure("Summary ready but temporary audio could not be deleted.")
    }
    val controller = controller()
    controller.start()
    dispatcher.scheduler.advanceUntilIdle()
    controller.stop()
    awaitTerminalState(controller)
    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
    assertThat((controller.state.value as CaptureSessionState.Failed).userMessage)
      .contains("temporary audio could not be deleted")
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
    assertThat((controller.state.value as CaptureSessionState.Failed).userMessage)
      .isEqualTo(CaptureSessionController.NO_AUDIO)
  }

  @Test
  fun `source failure with wav deletion failure surfaces cleanup error`() = runTest(dispatcher) {
    val factory = AudioSourceFactory { ThrowingAudioSource() }
    val controller = controller(
      factory = factory,
      wavProvider = undeletableWavProvider(),
    )
    controller.start()
    awaitTerminalState(controller)
    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
    assertThat((controller.state.value as CaptureSessionState.Failed).userMessage)
      .isEqualTo(CaptureSessionController.TEMP_AUDIO_CLEANUP_FAILED)
    assertThat(pipelineCalls).isEqualTo(0)
    assertThat(gatewayShutdowns).isEqualTo(1)
  }

  @Test
  fun `empty capture deletion failure surfaces cleanup error`() = runTest(dispatcher) {
    val emptyFixture = tmp.newFile("empty2.wav")
    writeMonoFixture(emptyFixture, samples = shortArrayOf())
    val controller = controller(
      fixtureFile = emptyFixture,
      wavProvider = undeletableWavProvider(),
    )
    controller.start()
    dispatcher.scheduler.advanceUntilIdle()
    controller.stop()
    awaitTerminalState(controller)
    assertThat(pipelineCalls).isEqualTo(0)
    assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
    assertThat((controller.state.value as CaptureSessionState.Failed).userMessage)
      .isEqualTo(CaptureSessionController.TEMP_AUDIO_CLEANUP_FAILED)
  }

  @Test
  fun `stale cleanup failure blocks capture without starting service`() = runTest(dispatcher) {
    val cacheDir = tmp.newFolder("cache")
    val stale = File(cacheDir, "${StaleAudioCleaner.SESSION_WAV_PREFIX}stale.wav").apply {
      writeBytes(byteArrayOf(1))
    }
    val lock = java.io.RandomAccessFile(stale, "rw")
    try {
      val controller = controller(
        staleCleaner = StaleAudioCleaner(FakeContext(cacheDir)),
      )
      controller.start()
      dispatcher.scheduler.advanceUntilIdle()
      assertThat(controller.state.value).isInstanceOf(CaptureSessionState.Failed::class.java)
      assertThat((controller.state.value as CaptureSessionState.Failed).userMessage)
        .isEqualTo(CaptureSessionController.TEMP_AUDIO_CLEANUP_FAILED)
      assertThat(gatewayStarts).isEqualTo(0)
      assertThat(pipelineCalls).isEqualTo(0)
    } finally {
      lock.close()
      stale.delete()
    }
  }

  private fun undeletableWavProvider(): WavFileProvider = WavFileProvider {
    val base = File.createTempFile("justsaid_session_", ".wav", outputDir)
    object : File(base.absolutePath) {
      override fun delete(): Boolean = false
    }
  }

  private fun controller(
    fixtureFile: File = fixture,
    factory: AudioSourceFactory = AudioSourceFactory { FileAudioSource(fixtureFile) },
    wavProvider: WavFileProvider = WavFileProvider {
      File.createTempFile("justsaid_session_", ".wav", outputDir)
    },
    captureGateway: CaptureServiceGateway = gateway,
    staleCleaner: StaleAudioCleaner = this.staleCleaner,
  ): CaptureSessionController = CaptureSessionController(
    audioSourceFactory = factory,
    wavFileProvider = wavProvider,
    sessionPipeline = pipeline,
    staleAudioCleaner = staleCleaner,
    captureServiceGateway = captureGateway,
    dispatcher = dispatcher,
  )

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
    sessionLabel = session.sessionLabel,
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

  private class ThrowingAudioSource : AudioSource {
    override val tier: CaptureTier = CaptureTier.MIC_ONLY
    override val channels: Int = 1
    override fun frames(): Flow<ShortArray> = flow {
      emit(shortArrayOf(100, 200))
      throw IOException("microphone source failed")
    }
  }
}
