package com.justsaid.app.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.justsaid.app.R
import com.justsaid.app.core.CallSummary
import com.justsaid.app.core.PromiseItem
import com.justsaid.app.export.SmsIntentBuilder
import com.justsaid.app.summary.SummaryMarkdown

/** Post-call summary route: the promises found in the call + Save / Send actions. */
@Composable
fun SummaryScreen(
    onDone: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    SummaryContent(
        state = state,
        onSave = viewModel::onSave,
        onDone = onDone,
    )
}

/** Stateless content so UI tests can drive it without Hilt. */
@Composable
fun SummaryContent(
    state: SummaryUiState,
    onSave: () -> Unit,
    onDone: () -> Unit,
) {
    val summary = state.summary ?: return
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryBody(summary)
            }

            SaveButton(saved = state.saved, onSave = onSave)
            SendSmsButton(summary)

            val doneDesc = stringResource(R.string.summary_done_content_desc)
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = doneDesc },
            ) {
                Text(stringResource(R.string.summary_done_button), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Header (who + when) and the promise list. Reused by the read-only history detail. */
@Composable
fun SummaryBody(summary: CallSummary) {
    Text(
        text = stringResource(
            R.string.summary_title_with,
            summary.contactName ?: summary.phoneNumber,
        ),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        text = SummaryMarkdown.formatDate(summary.createdAt),
        style = MaterialTheme.typography.titleLarge,
    )

    if (summary.items.isEmpty()) {
        Text(
            text = stringResource(R.string.summary_no_items),
            style = MaterialTheme.typography.bodyLarge,
        )
    } else {
        summary.items.forEach { PromiseRow(it) }
    }

    // Full STT text for debugging transcription; also helps when no tasks survived guardrails.
    if (summary.fullTranscript.isNotBlank()) {
        Text(
            text = stringResource(R.string.summary_heard_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = summary.fullTranscript,
            style = MaterialTheme.typography.bodyLarge,
        )
    } else if (summary.items.isEmpty()) {
        Text(
            text = stringResource(R.string.summary_heard_nothing),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PromiseRow(item: PromiseItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!item.confirmed) {
            UnconfirmedChip()
        }
        Row {
            Text(
                text = item.task + (item.quantity?.let { "  " + stringResource(R.string.summary_quantity, it) } ?: ""),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        // The verbatim proof, rendered in a quote style (G1: it IS in the transcript).
        Text(
            text = "\u201C${item.proofQuote}\u201D",
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun UnconfirmedChip() {
    Text(
        text = stringResource(R.string.summary_unconfirmed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onError,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun SaveButton(saved: Boolean, onSave: () -> Unit) {
    val label = if (saved) R.string.summary_saved_button else R.string.summary_save_button
    val desc = stringResource(
        if (saved) R.string.summary_saved_content_desc else R.string.summary_save_content_desc,
    )
    Button(
        onClick = onSave,
        enabled = !saved,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = desc },
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}

/** Opens the user's messaging app prefilled — never sends by itself. */
@Composable
fun SendSmsButton(summary: CallSummary) {
    val context = LocalContext.current
    val name = summary.contactName ?: summary.phoneNumber
    val unconfirmedLabel = stringResource(R.string.summary_unconfirmed)
    val desc = stringResource(R.string.summary_send_content_desc, name)
    Button(
        onClick = {
            val body = SummaryMarkdown.render(summary, unconfirmedLabel)
            context.startActivity(SmsIntentBuilder.build(summary.phoneNumber, body))
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = desc },
    ) {
        Text(
            text = stringResource(R.string.summary_send_button, name),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
