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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

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
    private val sessionMutex = Mutex()

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
        scope.launch { sessionMutex.withLock { startInternal(sessionLabel) } }
    }

    /** Ends capture, finalizes the WAV, stops the microphone FGS, and runs the pipeline once. */
    fun stop() {
        scope.launch { sessionMutex.withLock { stopInternal() } }
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

        if (!staleAudioCleaner.clean().isClean) {
            _state.value = CaptureSessionState.Failed(userMessage = TEMP_AUDIO_CLEANUP_FAILED)
            return
        }
        pendingLabel = sessionLabel

        val sessionId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val source = audioSourceFactory.create()
        val file = wavFileProvider.newWavFile()

        if (!captureServiceGateway.start()) {
            if (!deleteTempWavOrFail(file)) {
                captureServiceGateway.shutdown()
                return
            }
            _state.value = CaptureSessionState.Failed(userMessage = START_FAILED)
            return
        }

        val writer = WavWriter(file, channels = source.channels)
        try {
            writer.open()
        } catch (_: Exception) {
            captureServiceGateway.shutdown()
            if (!deleteTempWavOrFail(file)) return
            _state.value = CaptureSessionState.Failed(userMessage = START_FAILED)
            return
        }

        val job = scope.launch {
            try {
                source.frames().collect { frame ->
                    try {
                        writer.write(frame)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        failDuringRecording(START_FAILED)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failDuringRecording(START_FAILED)
            }
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

        stopCaptureIo(capture)
        captureServiceGateway.shutdown()

        if (capture.writer.dataBytes == 0L) {
            if (!deleteTempWavOrFail(capture.file)) return
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

        val result = try {
            sessionPipeline.process(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            JustSaidResult.Failure(PROCESSING_FAILED, e)
        }

        _state.value = when (result) {
            is JustSaidResult.Success -> CaptureSessionState.Completed(current.sessionId)
            is JustSaidResult.Failure -> CaptureSessionState.Failed(result.reason)
        }
    }

    private suspend fun failDuringRecording(userMessage: String) {
        sessionMutex.withLock {
            if (_state.value !is CaptureSessionState.Recording) return
            val capture = active
            active = null
            if (capture != null) {
                stopCaptureIo(capture)
                if (!deleteTempWavOrFail(capture.file)) {
                    captureServiceGateway.shutdown()
                    return
                }
            }
            captureServiceGateway.shutdown()
            _state.value = CaptureSessionState.Failed(userMessage)
        }
    }

    private fun deleteTempWavOrFail(file: java.io.File): Boolean {
        if (SessionWavFiles.deleteVerified(file)) return true
        _state.value = CaptureSessionState.Failed(userMessage = TEMP_AUDIO_CLEANUP_FAILED)
        return false
    }

    private suspend fun stopCaptureIo(capture: ActiveCapture) {
        capture.job.cancel()
        if (capture.job != coroutineContext[Job]) {
            capture.job.join()
        }
        try {
            capture.writer.close()
        } catch (_: Exception) {
            // Writer close must not escape; capture is already ending.
        }
    }

    private class ActiveCapture(
        val source: AudioSource,
        val writer: WavWriter,
        val file: java.io.File,
        val job: Job,
    )

    companion object {
        const val START_FAILED: String = "Could not start listening."
        const val STOP_FAILED: String = "Could not finish listening."
        const val NO_AUDIO: String = "No audio was captured."
        const val PROCESSING_FAILED: String = "Could not finish processing."
        const val TEMP_AUDIO_CLEANUP_FAILED: String = SessionWavFiles.TEMP_AUDIO_DELETION_FAILED
    }
}
