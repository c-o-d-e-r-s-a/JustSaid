package com.justsaid.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.justsaid.app.R
import com.justsaid.app.ui.summary.SendSmsButton
import com.justsaid.app.ui.summary.SummaryBody

/** Read-only view of one saved summary with Export / Send / Delete (T3/T4). */
@Composable
fun HistoryDetailScreen(
    onBack: () -> Unit,
    viewModel: HistoryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.shareIntents.collect { context.startActivity(it) }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    var confirmDelete by remember { mutableStateOf(false) }

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
                when (val summary = state.summary) {
                    null -> if (state.notFound) {
                        Text(
                            text = stringResource(R.string.detail_not_found),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    else -> SummaryBody(summary)
                }
            }

            if (state.exportFailed) {
                Text(
                    text = stringResource(R.string.export_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            state.summary?.let { summary ->
                val exportDesc = stringResource(R.string.detail_export_content_desc)
                Button(
                    onClick = viewModel::onExport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = exportDesc },
                ) {
                    Text(stringResource(R.string.detail_export_button), style = MaterialTheme.typography.labelLarge)
                }

                SendSmsButton(summary)

                val deleteDesc = stringResource(R.string.detail_delete_content_desc)
                Button(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = deleteDesc },
                ) {
                    Text(stringResource(R.string.detail_delete_button), style = MaterialTheme.typography.labelLarge)
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.detail_delete_confirm_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.detail_delete_confirm_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.onDelete()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.detail_delete_confirm_yes), style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.detail_delete_confirm_no), style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}
