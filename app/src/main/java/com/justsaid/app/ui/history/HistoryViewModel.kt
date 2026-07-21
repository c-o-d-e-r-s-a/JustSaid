package com.justsaid.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justsaid.app.core.CallSummary
import com.justsaid.app.data.repo.RoomSummaryRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Immutable state for the saved-calls list. */
data class HistoryUiState(
    val summaries: List<CallSummary> = emptyList(),
)

/** Streams the saved summaries, newest first, from the encrypted store. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    repo: RoomSummaryRepo,
) : ViewModel() {

    val state: StateFlow<HistoryUiState> = repo.observeAll()
        .map { HistoryUiState(summaries = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
