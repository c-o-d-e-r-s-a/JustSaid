package com.justsaid.app.audio

import com.justsaid.app.BuildConfig
import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.data.contacts.ContactResolver
import com.justsaid.app.data.repo.SettingsRepo
import com.justsaid.app.telecom.CallState
import com.justsaid.app.telecom.CallStateHolder
import com.justsaid.app.telecom.phoneNumber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/** Post-call pipeline phase, drives the full-screen loading modal in the UI. */
enum class PipelineState { IDLE, PROCESSING, DONE }

/**
 * Ties the LISTEN toggle + call lifecycle to the audio capture and the post-call pipeline
 * (AGENTS.md §4). Captures ONLY while (LISTEN on && call Active); on disconnect with audio
 * captured, hands the finished wav to [CallPipeline] and surfaces [PipelineState] for the
 * loading modal.
 *
 * Deliberately Android-free (no Context/AudioRecord here) — those live behind
 * [AudioSourceFactory], [WavFileProvider], and [ContactResolver] — so this whole state
 * machine is unit-testable on the JVM.
 */
@Singleton
class CaptureController @Inject constructor(
    private val callStateHolder: CallStateHolder,
    private val audioSourceFactory: AudioSourceFactory,
    private val wavFileProvider: WavFileProvider,
    private val contactResolver: ContactResolver,
    private val callPipeline: CallPipeline,
    private val settingsRepo: SettingsRepo,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _listenEnabled = MutableStateFlow(false)
    val listenEnabled: StateFlow<Boolean> = _listenEnabled.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _activeCaptureTier = MutableStateFlow<CaptureTier?>(null)
    /** Tier in use while LISTEN capture is running; null when idle. */
    val activeCaptureTier: StateFlow<CaptureTier?> = _activeCaptureTier.asStateFlow()

    private val _pipelineState = MutableStateFlow(PipelineState.IDLE)
    val pipelineState: StateFlow<PipelineState> = _pipelineState.asStateFlow()

    private var active: ActiveCapture? = null
    private var finished: RecordedCall? = null
    private var prev: CallState = CallState.Idle
    private var pipelineLaunched = false

    init {
        scope.launch {
            combine(callStateHolder.state, _listenEnabled, ::Pair).collect { (state, listen) ->
                onTick(state, listen)
            }
        }
    }

    /** UI toggles the big 🔴 LISTEN control through here (side effects via the ViewModel). */
    fun setListen(enabled: Boolean) {
        _listenEnabled.value = enabled
    }

    /** UI calls this after the post-call modal is dismissed to return to Idle. */
    fun onPostCallDismissed() {
        _pipelineState.value = PipelineState.IDLE
        pipelineLaunched = false
        callStateHolder.reset()
    }

    private suspend fun onTick(state: CallState, listen: Boolean) {
        if (isCallStart(prev, state)) {
            pipelineLaunched = false
            _pipelineState.value = PipelineState.IDLE
            finished = null
            prev = state
            val default = settingsRepo.alwaysListen.first()
            if (default != _listenEnabled.value) {
                _listenEnabled.value = default // re-enters onTick with the applied default
                return
            }
        }
        prev = state

        when (state) {
            is CallState.Active -> if (listen) startCapture(state.phoneNumber) else stopCapture(keep = true)
            is CallState.Ringing, is CallState.Held -> stopCapture(keep = true)
            is CallState.Disconnected -> {
                stopCapture(keep = true)
                finishCall()
            }
            is CallState.Idle -> stopCapture(keep = false)
        }
    }

    private suspend fun startCapture(number: String) {
        if (active != null) return
        // If a prior segment of this same call was buffered (e.g. after un-holding), drop it —
        // we keep only the latest contiguous segment and never leak an orphan wav.
        finished?.let { if (it.wavFile.exists()) it.wavFile.delete() }
        finished = null
        val source = audioSourceFactory.create()
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "capture_start tier=${source.tier} channels=${source.channels}")
        }
        val file = wavFileProvider.newWavFile()
        val writer = WavWriter(file, channels = source.channels)
        writer.open()
        val contactName = contactResolver.resolve(number)
        var peak = 0
        var sawSignal = false
        val job = scope.launch {
            source.frames().collect { frame ->
                writer.write(frame)
                if (frameHasLiveSignal(frame)) sawSignal = true
                val p = framePeakAmplitude(frame)
                if (p > peak) peak = p
            }
        }
        active = ActiveCapture(source, writer, file, number, contactName, job, { peak }, { sawSignal })
        _activeCaptureTier.value = source.tier
        _isCapturing.value = true
    }

    private suspend fun stopCapture(keep: Boolean) {
        val a = active ?: return
        active = null
        a.job.cancel()
        a.job.join()
        a.writer.close()
        _isCapturing.value = false
        _activeCaptureTier.value = null

        if (BuildConfig.DEBUG) {
            Log.i(
                TAG,
                "capture_stop tier=${a.source.tier} bytes=${a.writer.dataBytes} " +
                    "peak=${a.peak()} signal=${a.sawSignal()}",
            )
        }

        if (keep && a.writer.dataBytes > 0L) {
            finished = RecordedCall(
                wavFile = a.file,
                tier = a.source.tier,
                sampleRate = AudioSource.SAMPLE_RATE,
                channels = a.source.channels,
                phoneNumber = a.number,
                contactName = a.contactName,
                durationMs = a.writer.durationMs(),
            )
        } else {
            if (a.file.exists()) a.file.delete()
            finished = null
        }
    }

    /**
     * Terminal handling for a disconnected call, run exactly once (guarded by
     * [pipelineLaunched] against the framework emitting Disconnected twice). If audio was
     * captured it runs the pipeline behind the PROCESSING modal; otherwise it goes straight to
     * DONE so the UI can dismiss. The controller — not the UI — owns this decision, so there is
     * no race between two collectors observing the Disconnected transition.
     */
    private fun finishCall() {
        if (pipelineLaunched) return
        pipelineLaunched = true

        val call = finished
        if (call == null) {
            _pipelineState.value = PipelineState.DONE
            return
        }
        _pipelineState.value = PipelineState.PROCESSING
        scope.launch {
            try {
                callPipeline.process(call)
            } finally {
                finished = null
                _pipelineState.value = PipelineState.DONE
            }
        }
    }

    private fun isCallStart(prev: CallState, next: CallState): Boolean {
        val wasEnded = prev is CallState.Idle || prev is CallState.Disconnected
        val nowLive = next is CallState.Ringing || next is CallState.Active || next is CallState.Held
        return wasEnded && nowLive
    }

    private class ActiveCapture(
        val source: AudioSource,
        val writer: WavWriter,
        val file: java.io.File,
        val number: String,
        val contactName: String?,
        val job: Job,
        val peak: () -> Int,
        val sawSignal: () -> Boolean,
    )

    private companion object {
        const val TAG = "JustSaidCapture"
    }
}
