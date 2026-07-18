package com.justsaid.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Which whisper model to use. AUTO = multilingual base; EN = english-only small. */
enum class SttLanguageLock { AUTO, EN }

/**
 * User settings backed by DataStore Preferences. Exposes cold [Flow]s (UI observes)
 * and suspend setters (side effects go through here, MVVM). Defaults are chosen so a
 * fresh install is safe and private: nothing accepted, nothing always-on.
 */
@Singleton
class SettingsRepo @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val data: Flow<Preferences> = dataStore.data.catch { e ->
        // A corrupt/unreadable store must not crash the app; fall back to defaults.
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val legalAccepted: Flow<Boolean> = data.map { it[LEGAL_ACCEPTED] ?: false }
    val ttsNoticeEnabled: Flow<Boolean> = data.map { it[TTS_NOTICE_ENABLED] ?: false }
    val autoCleanupEnabled: Flow<Boolean> = data.map { it[AUTO_CLEANUP_ENABLED] ?: false }
    val alwaysListen: Flow<Boolean> = data.map { it[ALWAYS_LISTEN] ?: false }
    val sttLanguageLock: Flow<SttLanguageLock> = data.map { prefs ->
        when (prefs[STT_LANGUAGE_LOCK]) {
            SttLanguageLock.EN.name -> SttLanguageLock.EN
            else -> SttLanguageLock.AUTO
        }
    }

    suspend fun setLegalAccepted(value: Boolean) =
        edit(LEGAL_ACCEPTED, value)

    suspend fun setTtsNoticeEnabled(value: Boolean) =
        edit(TTS_NOTICE_ENABLED, value)

    suspend fun setAutoCleanupEnabled(value: Boolean) =
        edit(AUTO_CLEANUP_ENABLED, value)

    suspend fun setAlwaysListen(value: Boolean) =
        edit(ALWAYS_LISTEN, value)

    suspend fun setSttLanguageLock(value: SttLanguageLock) {
        dataStore.edit { it[STT_LANGUAGE_LOCK] = value.name }
    }

    private suspend fun edit(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val LEGAL_ACCEPTED = booleanPreferencesKey("legal_accepted")
        val TTS_NOTICE_ENABLED = booleanPreferencesKey("tts_notice_enabled")
        val AUTO_CLEANUP_ENABLED = booleanPreferencesKey("auto_cleanup_enabled")
        val ALWAYS_LISTEN = booleanPreferencesKey("always_listen")
        val STT_LANGUAGE_LOCK = stringPreferencesKey("stt_language_lock")
    }
}
