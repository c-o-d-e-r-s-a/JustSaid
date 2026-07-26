package com.justsaid.app.ui.incall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.justsaid.app.R

/** Route-level in-call overlay. Renders only while a call is live (state.visible). */
@Composable
fun InCallScreen(viewModel: InCallViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    InCallContent(
        state = state,
        onToggleListen = viewModel::onToggleListen,
        onAnswer = viewModel::onAnswer,
        onHangup = viewModel::onHangup,
        onToggleSpeaker = viewModel::onToggleSpeaker,
        onDismissMicOnlyHint = viewModel::onDismissMicOnlyHint,
    )
}

/** Stateless content so UI tests can drive it without Hilt. */
@Composable
fun InCallContent(
    state: InCallUiState,
    onToggleListen: (Boolean) -> Unit,
    onAnswer: () -> Unit,
    onHangup: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onDismissMicOnlyHint: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.heightIn(min = 24.dp))
            Text(
                text = state.displayName.ifBlank { stringResource(R.string.incall_unknown_caller) },
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(phaseLabel(state.phase)),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.heightIn(min = 8.dp))

            ListenToggle(
                enabled = state.listenEnabled,
                capturing = state.isCapturing,
                onToggle = onToggleListen,
            )

            if (state.phase == CallPhase.ACTIVE || state.phase == CallPhase.HELD) {
                SpeakerToggle(
                    speakerOn = state.speakerOn,
                    onToggle = onToggleSpeaker,
                )
            }

            if (state.showMicOnlySpeakerHint) {
                MicOnlySpeakerHint(onDismiss = onDismissMicOnlyHint)
            }

            Spacer(Modifier.weight(1f))

            if (state.phase == CallPhase.RINGING) {
                val answerDesc = stringResource(R.string.incall_answer_content_desc)
                Button(
                    onClick = onAnswer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .semantics { contentDescription = answerDesc },
                ) {
                    Text(stringResource(R.string.incall_answer), style = MaterialTheme.typography.labelLarge)
                }
            }

            val hangupDesc = stringResource(R.string.incall_hangup_content_desc)
            Button(
                onClick = onHangup,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics { contentDescription = hangupDesc },
            ) {
                Text(stringResource(R.string.incall_hangup), style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (state.showLoadingModal) {
        PostCallLoadingModal()
    }
}

@Composable
private fun SpeakerToggle(
    speakerOn: Boolean,
    onToggle: () -> Unit,
) {
    val descOn = stringResource(R.string.incall_speaker_content_desc_on)
    val descOff = stringResource(R.string.incall_speaker_content_desc_off)
    val label = if (speakerOn) R.string.incall_speaker_on else R.string.incall_speaker_off
    Button(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics { contentDescription = if (speakerOn) descOn else descOff },
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MicOnlySpeakerHint(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.incall_speaker_hint),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.incall_speaker_hint_dismiss))
        }
    }
}

@Composable
private fun ListenToggle(
    enabled: Boolean,
    capturing: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val descOn = stringResource(R.string.incall_listen_content_desc_on)
    val descOff = stringResource(R.string.incall_listen_content_desc_off)
    val label = if (enabled) R.string.incall_listen_on else R.string.incall_listen_off
    Button(
        onClick = { onToggle(!enabled) },
        colors = if (enabled) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics { contentDescription = if (enabled) descOn else descOff },
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
    }
}

/** Unskippable post-call modal (Constitution U3); auto-dismisses when the pipeline finishes. */
@Composable
private fun PostCallLoadingModal() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(R.string.incall_processing),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun phaseLabel(phase: CallPhase): Int = when (phase) {
    CallPhase.RINGING -> R.string.incall_phase_ringing
    CallPhase.ACTIVE -> R.string.incall_phase_active
    CallPhase.HELD -> R.string.incall_phase_held
    CallPhase.DISCONNECTED -> R.string.incall_phase_disconnected
    CallPhase.NONE -> R.string.incall_phase_active
}
