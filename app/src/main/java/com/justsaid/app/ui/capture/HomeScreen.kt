package com.justsaid.app.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.justsaid.app.R
import com.justsaid.app.core.CallSummary
import com.justsaid.app.session.CaptureSessionState
import com.justsaid.app.ui.summary.SummaryContent
import com.justsaid.app.ui.summary.SummaryReadMode
import com.justsaid.app.ui.summary.SummaryUiState
import com.justsaid.app.ui.summary.SummaryViewModel

/** Companion home: optional label, start/stop listening, permissions, processing, unsaved summary. */
@Composable
fun HomeScreen(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    summaryViewModel: SummaryViewModel = hiltViewModel(),
) {
    val captureState by homeViewModel.captureState.collectAsState()
    val pendingSummary by homeViewModel.pendingSummary.collectAsState()
    val summaryState by summaryViewModel.state.collectAsState()

    HomeContent(
        captureState = captureState,
        pendingSummary = pendingSummary,
        summaryState = summaryState,
        onStartListening = homeViewModel::startListening,
        onStopListening = homeViewModel::stopListening,
        onDismissError = homeViewModel::dismissError,
        onDismissSummary = homeViewModel::dismissSummary,
        onSave = summaryViewModel::onSave,
        onReadModeSelected = summaryViewModel::onReadModeSelected,
        onOpenHistory = onOpenHistory,
        onOpenSettings = onOpenSettings,
    )
}

/** Stateless content so UI tests can drive capture home without Hilt. */
@Composable
fun HomeContent(
    captureState: CaptureSessionState,
    pendingSummary: CallSummary?,
    summaryState: SummaryUiState,
    onStartListening: (String?) -> Unit,
    onStopListening: () -> Unit,
    onDismissError: () -> Unit,
    onDismissSummary: () -> Unit,
    onSave: () -> Unit,
    onReadModeSelected: (SummaryReadMode) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var labelText by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            CapturePermissionsCard()

            val labelEnabled = captureState is CaptureSessionState.Idle ||
                captureState is CaptureSessionState.Completed ||
                captureState is CaptureSessionState.Failed
            val labelDesc = stringResource(R.string.home_session_label_content_desc)
            OutlinedTextField(
                value = labelText,
                onValueChange = { labelText = it },
                enabled = labelEnabled,
                label = { Text(stringResource(R.string.home_session_label_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = labelDesc },
                singleLine = true,
            )

            CaptureControls(
                state = captureState,
                canStart = hasAllCapturePermissions(LocalContext.current),
                sessionLabel = labelText,
                onStart = onStartListening,
                onStop = onStopListening,
                onDismissError = onDismissError,
            )

            val summary = summaryState.summary ?: pendingSummary
            if (summary != null &&
                captureState !is CaptureSessionState.Recording &&
                captureState !is CaptureSessionState.Finalizing &&
                captureState !is CaptureSessionState.Processing
            ) {
                Text(
                    text = stringResource(R.string.home_summary_heading),
                    style = MaterialTheme.typography.titleLarge,
                )
                SummaryContent(
                    state = summaryState.copy(summary = summary),
                    onSave = onSave,
                    onReadModeSelected = onReadModeSelected,
                    onDone = onDismissSummary,
                    showDoneButton = true,
                    doneLabel = R.string.home_summary_dismiss_button,
                    doneContentDesc = R.string.home_summary_dismiss_content_desc,
                    embedded = true,
                )
            }

            val historyDesc = stringResource(R.string.history_open_content_desc)
            OutlinedButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics { contentDescription = historyDesc },
            ) {
                Text(
                    text = stringResource(R.string.history_open_button),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }

            val settingsDesc = stringResource(R.string.settings_open_content_desc)
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics { contentDescription = settingsDesc },
            ) {
                Text(
                    text = stringResource(R.string.settings_open_button),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CaptureControls(
    state: CaptureSessionState,
    canStart: Boolean,
    sessionLabel: String,
    onStart: (String?) -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
) {
    when (state) {
        is CaptureSessionState.Idle,
        is CaptureSessionState.Completed,
        -> {
            val desc = stringResource(R.string.home_start_content_desc)
            Button(
                onClick = { onStart(sessionLabel) },
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics { contentDescription = desc },
            ) {
                Text(
                    text = stringResource(R.string.home_start_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        is CaptureSessionState.Recording -> {
            val desc = stringResource(R.string.home_stop_content_desc)
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics { contentDescription = desc },
            ) {
                Text(
                    text = stringResource(R.string.home_stop_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = stringResource(R.string.home_listening_active),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is CaptureSessionState.Finalizing,
        is CaptureSessionState.Processing,
        -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.home_processing),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        is CaptureSessionState.Failed -> {
            Text(
                text = state.userMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            val dismissDesc = stringResource(R.string.home_error_dismiss_content_desc)
            OutlinedButton(
                onClick = onDismissError,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = dismissDesc },
            ) {
                Text(
                    text = stringResource(R.string.home_error_dismiss_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun CapturePermissionsCard() {
    val context = LocalContext.current
    var allGranted by remember { mutableStateOf(hasAllCapturePermissions(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        allGranted = hasAllCapturePermissions(context)
    }

    if (allGranted) {
        Text(
            text = stringResource(R.string.home_permissions_active),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(
            text = stringResource(R.string.home_permissions_prompt),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val buttonDesc = stringResource(R.string.home_permissions_content_desc)
        Button(
            onClick = { launcher.launch(requiredCapturePermissions()) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics { contentDescription = buttonDesc },
        ) {
            Text(
                text = stringResource(R.string.home_permissions_button),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun requiredCapturePermissions(): Array<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun hasAllCapturePermissions(context: Context): Boolean =
    requiredCapturePermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
