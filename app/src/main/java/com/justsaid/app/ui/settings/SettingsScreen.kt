package com.justsaid.app.ui.settings

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.justsaid.app.R
import com.justsaid.app.data.repo.SttLanguageLock
import com.justsaid.app.stt.WhisperLanguageCatalog

/**
 * Plain rows with big toggles (U1/U2): call language, spoken notice, auto-cleanup,
 * and Clear History behind a confirmation dialog (T3).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDownloadNeeded: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.downloadNeeded.collect { onDownloadNeeded() }
    }

    SettingsContent(
        state = state,
        onLanguageSelected = viewModel::onLanguageSelected,
        onSpokenLanguageToggled = viewModel::onSpokenLanguageToggled,
        onTtsNoticeChanged = viewModel::onTtsNoticeChanged,
        onAutoCleanupChanged = viewModel::onAutoCleanupChanged,
        onClearHistory = viewModel::onClearHistory,
        onBack = onBack,
    )
}

/** Stateless content so UI tests can drive it without Hilt. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onLanguageSelected: (SttLanguageLock) -> Unit,
    onSpokenLanguageToggled: (code: String, selected: Boolean) -> Unit,
    onTtsNoticeChanged: (Boolean) -> Unit,
    onAutoCleanupChanged: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            LanguageRow(selected = state.languageLock, onSelected = onLanguageSelected)

            if (state.languageLock == SttLanguageLock.AUTO) {
                SpokenLanguagesRow(
                    selected = state.spokenLanguages,
                    onToggled = onSpokenLanguageToggled,
                )
            }

            ToggleRow(
                title = stringResource(R.string.settings_tts_title),
                description = stringResource(R.string.settings_tts_desc),
                contentDesc = stringResource(R.string.settings_tts_content_desc),
                checked = state.ttsNoticeEnabled,
                onChanged = onTtsNoticeChanged,
            )

            ToggleRow(
                title = stringResource(R.string.settings_cleanup_title),
                description = stringResource(R.string.settings_cleanup_desc),
                contentDesc = stringResource(R.string.settings_cleanup_content_desc),
                checked = state.autoCleanupEnabled,
                onChanged = onAutoCleanupChanged,
            )

            val clearDesc = stringResource(R.string.settings_clear_content_desc)
            Button(
                onClick = { confirmClear = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = clearDesc },
            ) {
                Text(stringResource(R.string.settings_clear_button), style = MaterialTheme.typography.labelLarge)
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

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.settings_clear_confirm_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearHistory()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.settings_clear_confirm_yes), style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClear = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.settings_clear_confirm_no), style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

/** Two big mutually exclusive choices instead of a dropdown (no jargon, U1). */
@Composable
private fun LanguageRow(
    selected: SttLanguageLock,
    onSelected: (SttLanguageLock) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_language_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.settings_language_desc),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LanguageChoice(
                label = stringResource(R.string.settings_language_auto),
                contentDesc = stringResource(R.string.settings_language_auto_content_desc),
                selected = selected == SttLanguageLock.AUTO,
                onClick = { onSelected(SttLanguageLock.AUTO) },
                modifier = Modifier.weight(1f),
            )
            LanguageChoice(
                label = stringResource(R.string.settings_language_en),
                contentDesc = stringResource(R.string.settings_language_en_content_desc),
                selected = selected == SttLanguageLock.EN,
                onClick = { onSelected(SttLanguageLock.EN) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpokenLanguagesRow(
    selected: Set<String>,
    onToggled: (code: String, selected: Boolean) -> Unit,
) {
    val entriesWithLabels = WhisperLanguageCatalog.entries.map { entry ->
        entry to WhisperLanguageCatalog.displayName(entry.code)
    }
    val sortedEntries = entriesWithLabels.sortedBy { it.second }.map { it.first }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_spoken_languages_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.settings_spoken_languages_desc),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (entry in sortedEntries) {
                val label = WhisperLanguageCatalog.displayName(entry.code)
                val isSelected = entry.code in selected
                val stateLabel = stringResource(
                    if (isSelected) R.string.settings_spoken_language_selected
                    else R.string.settings_spoken_language_not_selected,
                )
                val chipDesc = stringResource(
                    R.string.settings_spoken_language_chip_content_desc,
                    label,
                    stateLabel,
                )
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggled(entry.code, !isSelected) },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.semantics { contentDescription = chipDesc },
                )
            }
        }
    }
}

@Composable
private fun LanguageChoice(
    label: String,
    contentDesc: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier
                .heightIn(min = 56.dp)
                .semantics { contentDescription = contentDesc },
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .heightIn(min = 56.dp)
                .semantics { contentDescription = contentDesc },
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    contentDesc: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChanged,
            modifier = Modifier.semantics { contentDescription = contentDesc },
        )
    }
}
