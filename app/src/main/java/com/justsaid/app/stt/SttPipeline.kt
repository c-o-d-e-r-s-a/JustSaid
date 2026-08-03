package com.justsaid.app.stt

import com.justsaid.app.audio.CaptureInput
import com.justsaid.app.audio.RecordedCall
import com.justsaid.app.audio.RecordedSession
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.core.Transcript
import com.justsaid.app.data.repo.SettingsRepo
import com.justsaid.app.data.repo.SttLanguageLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3's slice of the session pipeline: resolve the language setting and
 * transcribe. Summarization and the wav lifecycle belong to Phase 4's
 * [com.justsaid.app.pipeline.CallPipelineImpl], which owns the single
 * delete-in-finally point (Constitution A1) so deletion can never happen twice
 * or too early.
 */
@Singleton
class SttPipeline @Inject constructor(
    private val whisperEngine: WhisperEngine,
    private val settingsRepo: SettingsRepo,
) {

    /** Transcribes [session] using the whisper model matching the language setting. */
    suspend fun transcribe(session: RecordedSession): JustSaidResult<Transcript> {
        val (lock, spoken) = combine(
            settingsRepo.sttLanguageLock,
            settingsRepo.sttSpokenLanguages,
        ) { l, s -> l to s }.first()
        val (whisperLang, allowedCsv) = SttTranscribeHint.resolve(lock, spoken)
        return when (val result = whisperEngine.transcribe(session, whisperLang, allowedCsv)) {
            is JustSaidResult.Success -> {
                val detected = when {
                    lock == SttLanguageLock.EN -> "en"
                    spoken.size == 1 -> spoken.single()
                    else -> result.value.detectedLanguage
                }
                JustSaidResult.Success(result.value.copy(detectedLanguage = detected))
            }
            is JustSaidResult.Failure -> result
        }
    }

    /** Temporary bridge for [RecordedCall] consumers until task 4 migrates the pipeline. */
    suspend fun transcribe(call: RecordedCall): JustSaidResult<Transcript> =
        transcribe(
            RecordedSession(
                id = call.phoneNumber,
                wavFile = call.wavFile,
                input = CaptureInput.MICROPHONE_MONO,
                sampleRate = call.sampleRate,
                channels = call.channels,
                startedAt = 0L,
                durationMs = call.durationMs,
                sessionLabel = call.contactName,
            ),
        )
}
