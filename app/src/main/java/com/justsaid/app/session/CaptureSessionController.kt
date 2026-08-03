package com.justsaid.app.session

import com.justsaid.app.audio.AudioSource
import com.justsaid.app.audio.AudioSourceFactory
import com.justsaid.app.audio.CaptureInput
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.audio.SessionPipeline
import com.justsaid.app.audio.WavFileProvider
import com.justsaid.app.audio.WavWriter
import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.core.JustSaidResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Explicit start/stop state machine for manual microphone capture. Does not observe
 * phone calls or start itself — only a visible activity invokes [start] after permission.
 */
@Singleton
class CaptureSessionController @Inject constructor(
  private val audioSourceFactory: AudioSourceFactory,
  private val wavFileProvider: WavFileProvider,
  private val sessionPipeline: SessionPipeline,
  private val staleAudioCleaner: StaleAudioCleaner,
  private val captureServiceGateway: CaptureServiceGateway,
  @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)

  private val _state = MutableStateFlow<CaptureSessionState>(CaptureSessionState.Idle)
  val state: StateFlow<CaptureSessionState> = _state.asStateFlow()

  private var active: ActiveCapture? = null
  private var pendingLabel: String? = null
  private var pipelineLaunched = false

  /**
   * Begins capture after stale temp cleanup. Safe to call only from a visible activity
   * once [android.Manifest.permission.RECORD_AUDIO] is granted.
   */
  fun start(sessionLabel: String? = null) {
    scope.launch { startInternal(sessionLabel) }
  }

  /** Ends capture, finalizes the WAV, stops the microphone FGS, and runs the pipeline once. */
  fun stop() {
    scope.launch { stopInternal() }
  }

  /** Returns UI/state machine to idle after [CaptureSessionState.Completed] or [CaptureSessionState.Failed]. */
  fun reset() {
    _state.value = CaptureSessionState.Idle
    pipelineLaunched = false
  }

  private suspend fun startInternal(sessionLabel: String?) {
    when (_state.value) {
      is CaptureSessionState.Recording,
      is CaptureSessionState.Finalizing,
      is CaptureSessionState.Processing,
      -> return
      is CaptureSessionState.Idle,
      is CaptureSessionState.Completed,
      is CaptureSessionState.Failed,
      -> Unit
    }

    staleAudioCleaner.clean()
    pendingLabel = sessionLabel

    val sessionId = UUID.randomUUID().toString()
    val startedAt = System.currentTimeMillis()
    val source = audioSourceFactory.create()
    val file = wavFileProvider.newWavFile()
    val writer = WavWriter(file, channels = source.channels)

    try {
      writer.open()
    } catch (e: Exception) {
      if (file.exists()) file.delete()
      _state.value = CaptureSessionState.Failed(userMessage = START_FAILED)
      return
    }

    if (!captureServiceGateway.start()) {
      writer.close()
      if (file.exists()) file.delete()
      _state.value = CaptureSessionState.Failed(userMessage = START_FAILED)
      return
    }

    val job = scope.launch {
      source.frames().collect { frame -> writer.write(frame) }
    }
    active = ActiveCapture(source, writer, file, job)
    pipelineLaunched = false
    _state.value = CaptureSessionState.Recording(sessionId, startedAt)
  }

  private suspend fun stopInternal() {
    val current = _state.value
    if (current !is CaptureSessionState.Recording) return

    _state.value = CaptureSessionState.Finalizing(current.sessionId)

    val capture = active
    active = null
    if (capture == null) {
      captureServiceGateway.shutdown()
      _state.value = CaptureSessionState.Failed(userMessage = STOP_FAILED)
      return
    }

    capture.job.cancel()
    capture.job.join()
    capture.writer.close()
    captureServiceGateway.shutdown()

    if (capture.writer.dataBytes == 0L) {
      if (capture.file.exists()) capture.file.delete()
      _state.value = CaptureSessionState.Failed(userMessage = NO_AUDIO)
      return
    }

    if (pipelineLaunched) return
    pipelineLaunched = true

    val session = RecordedSession(
      id = current.sessionId,
      wavFile = capture.file,
      input = CaptureInput.MICROPHONE_MONO,
      sampleRate = AudioSource.SAMPLE_RATE,
      channels = capture.source.channels,
      startedAt = current.startedAt,
      durationMs = capture.writer.durationMs(),
      sessionLabel = pendingLabel,
    )

    _state.value = CaptureSessionState.Processing(current.sessionId)

    when (val result = sessionPipeline.process(session)) {
      is JustSaidResult.Success -> _state.value = CaptureSessionState.Completed(current.sessionId)
      is JustSaidResult.Failure -> _state.value = CaptureSessionState.Failed(result.reason)
    }
  }

  private class ActiveCapture(
    val source: AudioSource,
    val writer: WavWriter,
    val file: java.io.File,
    val job: Job,
  )

  private companion object {
    const val START_FAILED: String = "Could not start listening."
    const val STOP_FAILED: String = "Could not finish listening."
    const val NO_AUDIO: String = "No audio was captured."
  }
}
