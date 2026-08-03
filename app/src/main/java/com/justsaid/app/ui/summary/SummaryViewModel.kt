package com.justsaid.app.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.data.repo.SummaryRepo
import com.justsaid.app.llm.LlmEngine
import com.justsaid.app.summary.SummaryEnglishTranslator
import com.justsaid.app.summary.SummaryEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SummaryReadMode { AsHeard, English }

/** Immutable state for the post-call summary screen. */
data class SummaryUiState(
    val summary: CallSummary? = null,
    val saved: Boolean = false,
    val readMode: SummaryReadMode = SummaryReadMode.AsHeard,
    val englishView: SummaryEnglishTranslator.View? = null,
    val englishLoading: Boolean = false,
    val englishFailed: Boolean = false,
)

/**
 * Presents the freshly produced [CallSummary] handed over by the pipeline via
 * [SummaryEvents]. A summary arriving with a storage id (the pipeline persisted
 * it) starts in the "saved" state; otherwise the Save button writes it through
 * [SummaryRepo]. The event is cleared when this screen goes away, so a stale
 * summary never re-opens the screen.
 */
@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val summaryEvents: SummaryEvents,
    private val repo: SummaryRepo,
    private val llmEngine: LlmEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(SummaryUiState())
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            summaryEvents.latest.collect { summary ->
                _state.update { current ->
                    if (summary == null) {
                        SummaryUiState()
                    } else {
                        SummaryUiState(
                            summary = summary,
                            saved = summary.id != 0L,
                            readMode = SummaryReadMode.AsHeard,
                            englishView = null,
                            englishLoading = false,
                            englishFailed = false,
                        )
                    }
                }
            }
        }
    }

    fun onReadModeSelected(mode: SummaryReadMode) {
        _state.update { it.copy(readMode = mode, englishFailed = false) }
        if (mode == SummaryReadMode.English) {
            maybeLoadEnglish()
        }
    }

    private fun maybeLoadEnglish() {
        val summary = _state.value.summary ?: return
        if (_state.value.englishView != null || _state.value.englishLoading) return
        _state.update { it.copy(englishLoading = true, englishFailed = false) }
        viewModelScope.launch {
            when (val result = llmEngine.generate(SummaryEnglishTranslator.buildLlmPrompt(summary))) {
                is JustSaidResult.Success -> {
                    val view = SummaryEnglishTranslator.parse(result.value, summary)
                    _state.update {
                        it.copy(
                            englishLoading = false,
                            englishView = view,
                            englishFailed = view == null,
                        )
                    }
                }
                is JustSaidResult.Failure ->
                    _state.update { it.copy(englishLoading = false, englishFailed = true) }
            }
        }
    }

    fun onSave() {
        val current = _state.value.summary ?: return
        if (_state.value.saved) return
        viewModelScope.launch {
            when (val result = repo.save(current)) {
                is JustSaidResult.Success ->
                    _state.value = _state.value.copy(summary = result.value, saved = true)
                is JustSaidResult.Failure -> Unit // button stays available for a retry (U4)
            }
        }
    }

    /** The summary has been consumed; do not re-open the screen for it. */
    override fun onCleared() {
        summaryEvents.clear()
    }
}
