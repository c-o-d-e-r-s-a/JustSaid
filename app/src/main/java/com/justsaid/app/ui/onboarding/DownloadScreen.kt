package com.justsaid.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.justsaid.app.R

/**
 * Route-level download screen. Blocks progress to Home until every required model
 * is present. Auto-advances via [onComplete] when the download succeeds.
 */
@Composable
fun DownloadScreen(
    onComplete: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }
    DownloadContent(state = state, onRetry = viewModel::retry)
}

/** Stateless content, so UI tests can drive it without Hilt. */
@Composable
fun DownloadContent(
    state: DownloadUiState,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.download_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.download_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.download_wifi_note),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = stringResource(R.string.download_overall_label),
            style = MaterialTheme.typography.titleLarge,
        )
        LinearProgressIndicator(
            progress = { state.overallPercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 12.dp),
        )
        Text(
            text = stringResource(R.string.download_percent, state.overallPercent) +
                "  •  " +
                stringResource(
                    R.string.download_progress_mb,
                    state.overallDownloadedMb,
                    state.overallTotalMb,
                ),
            style = MaterialTheme.typography.bodyMedium,
        )

        state.files.forEach { file ->
            FileProgressRow(file)
        }

        if (state.isComplete) {
            Text(
                text = stringResource(R.string.download_done),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (state.isError) {
            Text(
                text = stringResource(R.string.download_error),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            val retryDesc = stringResource(R.string.download_retry_content_desc)
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics { contentDescription = retryDesc },
            ) {
                Text(
                    text = stringResource(R.string.download_retry_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun FileProgressRow(file: FileRow) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = file.name, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { file.percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 8.dp),
        )
        Text(
            text = stringResource(R.string.download_progress_mb, file.downloadedMb, file.totalMb),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
