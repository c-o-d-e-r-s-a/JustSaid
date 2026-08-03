package com.justsaid.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.justsaid.app.R
import com.justsaid.app.core.CallSummary
import com.justsaid.app.summary.SummaryMarkdown

/** Saved-calls list: newest first, one big tappable row per call (U1/U2). */
@Composable
fun HistoryScreen(
    onOpenSummary: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    HistoryContent(state = state, onOpenSummary = onOpenSummary, onBack = onBack)
}

/** Stateless content so UI tests can drive it without Hilt. */
@Composable
fun HistoryContent(
    state: HistoryUiState,
    onOpenSummary: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            if (state.summaries.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.summaries, key = { it.id }) { summary ->
                        HistoryRow(summary = summary, onOpen = { onOpenSummary(summary.id) })
                    }
                }
            }

            val backDesc = stringResource(R.string.history_back_content_desc)
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = backDesc },
            ) {
                Text(stringResource(R.string.summary_done_button), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun HistoryRow(summary: CallSummary, onOpen: () -> Unit) {
    val label = summary.sessionLabel?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.history_unlabeled)
    val desc = stringResource(R.string.history_row_content_desc, label)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onOpen)
            .semantics { contentDescription = desc },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.titleLarge)
            Text(
                text = SummaryMarkdown.formatDate(summary.createdAt) +
                    "  •  " +
                    stringResource(R.string.history_item_count, summary.items.size),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
