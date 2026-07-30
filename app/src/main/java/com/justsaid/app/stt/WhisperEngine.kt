package com.justsaid.app.stt

import com.justsaid.app.audio.CaptureTier
import com.justsaid.app.audio.RecordedCall
import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.ModelPaths
import com.justsaid.app.core.Speaker
import com.justsaid.app.core.Transcript
import com.justsaid.app.core.TranscriptSegment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Kotlin orchestrator for whisper.cpp: decodes the wav, gates silence (N5), chunks
 * into ~30 s windows with ~1 s overlap, runs every window through ONE native context
 * (N1), dedupes overlap text, and tags speakers (S1/S2).
 *
 * The three `external fun`s here are the only JNI entry points; their mangled
 * symbols live in `cpp/justsaid_whisper_jni.cpp` (AGENTS.md §3). All orchestration
 * goes through [NativeWhisperBridge] so JVM tests inject a fake and never load the
 * `.so` — which is also why the library loads lazily on first real use instead of in
 * a `companion init`.
 */
@Singleton
class WhisperEngine(
    private val modelPaths: ModelPaths,
    private val audioDecoder: AudioDecoder,
    private val vadGate: VadGate,
    private val dispatcher: CoroutineDispatcher,
    private val params: WhisperParams,
    bridgeOverride: NativeWhisperBridge?,
) {

    @Inject
    constructor(
        modelPaths: ModelPaths,
        audioDecoder: AudioDecoder,
        vadGate: VadGate,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(modelPaths, audioDecoder, vadGate, dispatcher, WhisperParams(), null)

    private val bridge: NativeWhisperBridge = bridgeOverride ?: RealBridge()

    // ── JNI surface (do not rename: symbols are mangled against this class) ──
    external fun nativeInit(modelPath: String, threads: Int): Long
    external fun nativeTranscribe(
        handle: Long,
        pcm: FloatArray,
        lang: String,
        translate: Boolean,
        allowedLanguagesCsv: String,
    ): String
    external fun nativeFree(handle: Long)

    private inner class RealBridge : NativeWhisperBridge {
        override fun init(modelPath: String, threads: Int): Long {
            loadNativeLibrary()
            return nativeInit(modelPath, threads)
        }

        override fun transcribe(
            handle: Long,
            pcm: FloatArray,
            lang: String,
            translate: Boolean,
            allowedLanguagesCsv: String,
        ): String = nativeTranscribe(handle, pcm, lang, translate, allowedLanguagesCsv)

        override fun free(handle: Long) = nativeFree(handle)
    }

    /**
     * Full-call transcription. [whisperLanguage] is whisper's language hint (`auto`, `en`,
     * or a pinned ISO code). [allowedLanguagesCsv] restricts auto-detect when non-empty.
     */
    suspend fun transcribe(
        call: RecordedCall,
        whisperLanguage: String,
        allowedLanguagesCsv: String = "",
    ): JustSaidResult<Transcript> =
        withContext(dispatcher) {
            val modelFile = modelPaths.sttModelFile()
            if (!modelFile.exists()) {
                return@withContext JustSaidResult.Failure("STT model missing: ${modelFile.name}")
            }

            val audio = try {
                audioDecoder.decode(call.wavFile)
            } catch (e: Exception) {
                return@withContext JustSaidResult.Failure("could not decode call audio", e)
            }

            val handle = bridge.init(modelFile.absolutePath, WhisperParams.threadCount())
            if (handle == 0L) {
                return@withContext JustSaidResult.Failure("whisper context init failed")
            }

            try {
                val pin = LanguagePin(requested = whisperLanguage)
                val segments = if (call.tier == CaptureTier.STEREO && audio.isStereo) {
                    // S1: L = local user, R = remote party; same context sequentially (N1).
                    transcribeChannel(handle, audio.left!!, Speaker.LOCAL, pin, allowedLanguagesCsv) +
                        transcribeChannel(handle, audio.right!!, Speaker.REMOTE, pin, allowedLanguagesCsv)
                } else {
                    transcribeChannel(handle, audio.mono, Speaker.UNKNOWN, pin, allowedLanguagesCsv)
                }
                val detectedLanguage = when {
                    whisperLanguage == "en" -> "en"
                    whisperLanguage != "auto" -> whisperLanguage
                    else -> pin.detectedLanguage
                }
                JustSaidResult.Success(
                    Transcript(segments.sortedBy { it.startMs }, detectedLanguage = detectedLanguage),
                )
            } catch (e: Exception) {
                JustSaidResult.Failure("transcription failed", e)
            } finally {
                bridge.free(handle)
            }
        }

    /**
     * Sliding-window pass over one channel. Windows advance by chunk−overlap; silent
     * windows are skipped by the VAD; segments in a window's leading overlap zone are
     * dropped because the previous window already emitted them.
     *
     * After the first successful auto-detect window, pins whisper's language hint so
     * later chunks stay in the same script (whisper recommendation for multilingual).
     */
    private class LanguagePin(val requested: String) {
        var detectedLanguage: String? = null
            private set

        fun hintForWindow(): String = when {
            requested != "auto" -> requested
            detectedLanguage != null -> detectedLanguage!!
            else -> "auto"
        }

        fun absorb(chunk: WhisperJson.TranscriptionChunk) {
            if (requested != "auto") return
            chunk.detectedLanguage?.let { detectedLanguage = it }
        }
    }

    private fun transcribeChannel(
        handle: Long,
        pcm: FloatArray,
        speaker: Speaker,
        language: LanguagePin,
        allowedLanguagesCsv: String,
    ): List<TranscriptSegment> {
        val out = mutableListOf<TranscriptSegment>()
        val overlapMs = params.overlapSamples * 1000L / params.sampleRate
        var start = 0
        while (start < pcm.size) {
            // A tail shorter than the overlap was fully covered by the previous window.
            if (start > 0 && pcm.size - start <= params.overlapSamples) break

            val end = min(start + params.chunkSamples, pcm.size)
            if (vadGate.hasSpeech(pcm, start, end - start)) {
                val window = padToMinWindow(pcm.copyOfRange(start, end))
                val json = bridge.transcribe(
                    handle,
                    window,
                    language.hintForWindow(),
                    params.translate,
                    allowedLanguagesCsv,
                )
                val windowStartMs = start * 1000L / params.sampleRate
                val chunk = WhisperJson.parseChunk(json)
                language.absorb(chunk)

                for (seg in chunk.segments) {
                    val text = seg.text.trim()
                    if (text.isEmpty()) continue
                    // Overlap dedupe: segment midpoint inside the re-heard first second.
                    if (start > 0 && (seg.t0Ms + seg.t1Ms) / 2 < overlapMs) continue
                    // Guard against whisper re-emitting the identical previous line.
                    if (out.isNotEmpty() && normalize(out.last().text) == normalize(text)) continue
                    out += TranscriptSegment(speaker, text, windowStartMs + seg.t0Ms, windowStartMs + seg.t1Ms)
                }
            }
            if (end == pcm.size) break
            start += params.strideSamples
        }
        return out
    }

    /** whisper_full rejects input under ~1 s; zero-pad trailing slivers up front. */
    private fun padToMinWindow(window: FloatArray): FloatArray {
        val minSamples = (params.sampleRate * MIN_WINDOW_SECONDS).toInt()
        return if (window.size >= minSamples) window else window.copyOf(minSamples)
    }

    private fun normalize(text: String) = text.lowercase().replace(WHITESPACE, " ").trim()

    private companion object {
        const val MIN_WINDOW_SECONDS = 1.2
        val WHITESPACE = Regex("\\s+")

        @Volatile
        private var libraryLoaded = false

        fun loadNativeLibrary() {
            if (!libraryLoaded) {
                synchronized(this) {
                    if (!libraryLoaded) {
                        System.loadLibrary("justsaid_native")
                        libraryLoaded = true
                    }
                }
            }
        }
    }
}
