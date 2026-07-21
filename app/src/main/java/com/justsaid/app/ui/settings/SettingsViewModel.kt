package com.justsaid.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justsaid.app.core.DefaultDispatcher
import com.justsaid.app.core.ModelPaths
import com.justsaid.app.data.repo.RoomSummaryRepo
import com.justsaid.app.data.repo.SettingsRepo
import com.justsaid.app.data.repo.SttLanguageLock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Immutable state for the settings screen. */
data class SettingsUiState(
    val languageLock: SttLanguageLock = SttLanguageLock.AUTO,
    val ttsNoticeEnabled: Boolean = false,
    val autoCleanupEnabled: Boolean = false,
)

/**
 * Binds the settings rows to [SettingsRepo] and owns the two destructive/side-effect
 * actions: Clear History (confirmed in the UI first) and the language switch, which
 * emits [downloadNeeded] when the newly selected STT model is not on disk yet so the
 * NavHost can send the user through the Phase 1 downloader.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepo,
    private val summaryRepo: RoomSummaryRepo,
    private val modelPaths: ModelPaths,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepo.sttLanguageLock,
        settingsRepo.ttsNoticeEnabled,
        settingsRepo.autoCleanupEnabled,
    ) { lock, tts, cleanup ->
        SettingsUiState(languageLock = lock, ttsNoticeEnabled = tts, autoCleanupEnabled = cleanup)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _downloadNeeded = MutableSharedFlow<Unit>()
    val downloadNeeded: SharedFlow<Unit> = _downloadNeeded.asSharedFlow()

    fun onLanguageSelected(lock: SttLanguageLock) {
        viewModelScope.launch {
            settingsRepo.setSttLanguageLock(lock)
            // The model for the new language may not be downloaded yet (ModelPaths
            // resolves against the just-saved setting; checked off the main thread).
            val ready = withContext(dispatcher) { modelPaths.modelsReady() }
            if (!ready) _downloadNeeded.emit(Unit)
        }
    }

    fun onTtsNoticeChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setTtsNoticeEnabled(enabled) }
    }

    fun onAutoCleanupChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoCleanupEnabled(enabled) }
    }

    /** T3: wipes every saved summary. The UI shows the confirmation dialog first. */
    fun onClearHistory() {
        viewModelScope.launch { summaryRepo.deleteAll() }
    }
}
