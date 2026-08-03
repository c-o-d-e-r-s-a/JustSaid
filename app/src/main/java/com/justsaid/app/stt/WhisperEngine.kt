package com.justsaid.app.stt

import com.justsaid.app.audio.RecordedSession
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
 * Kotlin orchestrator for whisper.cpp: probes the wav, decodes bounded ~30 s mono
 * windows, gates silence (N5), runs every window through ONE native context (N1),
 * dedupes overlap text, and tags every segment [Speaker.UNKNOWN] (S1).
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
     * Full-session transcription. [whisperLanguage] is whisper's language hint (`auto`, `en`,
     * or a pinned ISO code). [allowedLanguagesCsv] restricts auto-detect when non-empty.
     */
    suspend fun transcribe(
        session: RecordedSession,
        whisperLanguage: String,
        allowedLanguagesCsv: String = "",
    ): JustSaidResult<Transcript> =
        withContext(dispatcher) {
            val modelFile = modelPaths.sttModelFile()
            if (!modelFile.exists()) {
                return@withContext JustSaidResult.Failure("STT model missing: ${modelFile.name}")
            }

            val info = try {
                audioDecoder.probe(session.wavFile)
            } catch (e: Exception) {
                return@withContext JustSaidResult.Failure("could not decode session audio", e)
            }

            val durationMs = when {
                session.durationMs > 0L -> session.durationMs
                else -> info.durationMs
            }
            if (durationMs > WhisperParams.MAX_SESSION_DURATION_MS) {
                return@withContext JustSaidResult.Failure(SESSION_TOO_LONG_MESSAGE)
            }

            val handle = bridge.init(modelFile.absolutePath, WhisperParams.threadCount())
            if (handle == 0L) {
                return@withContext JustSaidResult.Failure("whisper context init failed")
            }

            try {
                val pin = LanguagePin(requested = whisperLanguage)
                val segments = transcribeSession(
                    handle = handle,
                    file = session.wavFile,
                    info = info,
                    language = pin,
                    allowedLanguagesCsv = allowedLanguagesCsv,
                )
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

    private fun transcribeSession(
        handle: Long,
        file: java.io.File,
        info: WavInfo,
        language: LanguagePin,
        allowedLanguagesCsv: String,
    ): List<TranscriptSegment> {
        val totalSamples16k = samplesAt16k(info)
        val out = mutableListOf<TranscriptSegment>()
        val overlapMs = params.overlapSamples * 1000L / params.sampleRate
        var startSample16k = 0

        while (startSample16k < totalSamples16k) {
            if (startSample16k > 0 && totalSamples16k - startSample16k <= params.overlapSamples) break

            val endSample16k = min(startSample16k + params.chunkSamples, totalSamples16k)
            val sourceStartFrame = sourceFrameFor16kSample(info, startSample16k)
            val sourceEndFrame = sourceFrameFor16kSample(info, endSample16k)
            val window = audioDecoder.decodeMonoWindow(
                file,
                info,
                sourceStartFrame,
                sourceEndFrame - sourceStartFrame,
            )

            if (vadGate.hasSpeech(window)) {
                val padded = padToMinWindow(window)
                val json = bridge.transcribe(
                    handle,
                    padded,
                    language.hintForWindow(),
                    params.translate,
                    allowedLanguagesCsv,
                )
                val windowStartMs = startSample16k * 1000L / params.sampleRate
                val chunk = WhisperJson.parseChunk(json)
                language.absorb(chunk)

                for (seg in chunk.segments) {
                    val text = seg.text.trim()
                    if (text.isEmpty()) continue
                    if (startSample16k > 0 && (seg.t0Ms + seg.t1Ms) / 2 < overlapMs) continue
                    if (out.isNotEmpty() && normalize(out.last().text) == normalize(text)) continue
                    out += TranscriptSegment(
                        Speaker.UNKNOWN,
                        text,
                        windowStartMs + seg.t0Ms,
                        windowStartMs + seg.t1Ms,
                    )
                }
            }
            if (endSample16k == totalSamples16k) break
            startSample16k += params.strideSamples
        }
        return out
    }

    private fun samplesAt16k(info: WavInfo): Int {
        if (info.frameCount == 0) return 0
        return if (info.sampleRate == params.sampleRate) {
            info.frameCount
        } else {
            (info.frameCount.toLong() * params.sampleRate / info.sampleRate).toInt()
        }
    }

    private fun sourceFrameFor16kSample(info: WavInfo, sample16k: Int): Int {
        if (info.sampleRate == params.sampleRate) return sample16k.coerceAtMost(info.frameCount)
        return (sample16k.toLong() * info.sampleRate / params.sampleRate).toInt().coerceAtMost(info.frameCount)
    }

    /** whisper_full rejects input under ~1 s; zero-pad trailing slivers up front. */
    private fun padToMinWindow(window: FloatArray): FloatArray {
        val minSamples = (params.sampleRate * MIN_WINDOW_SECONDS).toInt()
        return if (window.size >= minSamples) window else window.copyOf(minSamples)
    }

    private fun normalize(text: String) = text.lowercase().replace(WHITESPACE, " ").trim()

    private companion object {
        const val MIN_WINDOW_SECONDS = 1.2
        const val SESSION_TOO_LONG_MESSAGE =
            "This recording is too long to process right now. Please keep sessions under 15 minutes."
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
