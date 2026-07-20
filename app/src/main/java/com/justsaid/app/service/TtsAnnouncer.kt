package com.justsaid.app.service

import android.content.Context
import android.speech.tts.TextToSpeech
import com.justsaid.app.R
import com.justsaid.app.data.repo.SettingsRepo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speaks the recording notice once when a call becomes Active, if the user enabled it
 * (off by default). Plays on the DEVICE SPEAKER via [TextToSpeech]; Android does not
 * guarantee injecting TTS into the call's uplink stream, so the remote party may not hear
 * it — a documented limitation (Phase 2 doc, "Optional TTS notice").
 */
@Singleton
class TtsAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepo,
) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val pending = AtomicBoolean(false)

    suspend fun maybeAnnounce() {
        if (!settingsRepo.ttsNoticeEnabled.first()) return
        ensureInit()
        if (ready) speakNow() else pending.set(true)
    }

    private fun ensureInit() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready && pending.getAndSet(false)) speakNow()
        }
    }

    private fun speakNow() {
        tts?.speak(
            context.getString(R.string.tts_recording_notice),
            TextToSpeech.QUEUE_FLUSH,
            null,
            "justsaid_recording_notice",
        )
    }

    fun shutdown() {
        pending.set(false)
        ready = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
