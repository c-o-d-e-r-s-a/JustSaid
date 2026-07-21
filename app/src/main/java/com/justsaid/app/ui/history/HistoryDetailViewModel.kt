package com.justsaid.app.ui.history

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.JustSaidResult
import com.justsaid.app.data.repo.RoomSummaryRepo
import com.justsaid.app.export.SummaryExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable state for the read-only history detail. */
data class HistoryDetailUiState(
    val summary: CallSummary? = null,
    val notFound: Boolean = false,
    val deleted: Boolean = false,
    val exportFailed: Boolean = false,
)

/**
 * Loads one saved summary and drives its Export / Delete actions. Share intents
 * come out through [shareIntents] so the screen (not the ViewModel) launches them.
 */
@HiltViewModel
class HistoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: RoomSummaryRepo,
    private val exporter: SummaryExporter,
) : ViewModel() {

    private val summaryId: Long = checkNotNull(savedStateHandle["id"])

    private val _state = MutableStateFlow(HistoryDetailUiState())
    val state: StateFlow<HistoryDetailUiState> = _state.asStateFlow()

    private val _shareIntents = MutableSharedFlow<Intent>()
    val shareIntents: SharedFlow<Intent> = _shareIntents.asSharedFlow()

    init {
        viewModelScope.launch {
            val summary = repo.getById(summaryId)
            _state.value = HistoryDetailUiState(summary = summary, notFound = summary == null)
        }
    }

    /** Exports this summary as a large-font PDF and emits the share chooser. */
    fun onExport() {
        val summary = _state.value.summary ?: return
        viewModelScope.launch {
            when (val result = exporter.exportPdf(listOf(summary))) {
                is JustSaidResult.Success -> {
                    _state.value = _state.value.copy(exportFailed = false)
                    _shareIntents.emit(exporter.shareIntent(result.value))
                }
                is JustSaidResult.Failure ->
                    _state.value = _state.value.copy(exportFailed = true)
            }
        }
    }

    /** Deletes this summary (already confirmed by the dialog in the UI). */
    fun onDelete() {
        viewModelScope.launch {
            repo.deleteById(summaryId)
            _state.value = _state.value.copy(deleted = true)
        }
    }
}
