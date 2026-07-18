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
 * Route-level legal screen. Unskippable: there is no back or skip path; the only
 * way forward is the "I Agree" button, which sets [SettingsRepo.setLegalAccepted].
 */
@Composable
fun LegalScreen(
    onAgreed: () -> Unit,
    viewModel: LegalViewModel = hiltViewModel(),
) {
    val accepted by viewModel.accepted.collectAsState()
    LaunchedEffect(accepted) {
        if (accepted) onAgreed()
    }
    LegalContent(onAgree = viewModel::onAgree)
}

/** Stateless content, so UI tests can drive it without Hilt. */
@Composable
fun LegalContent(onAgree: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.legal_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.legal_body),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        )
        val agreeDesc = stringResource(R.string.legal_agree_content_desc)
        Button(
            onClick = onAgree,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics { contentDescription = agreeDesc },
        ) {
            Text(
                text = stringResource(R.string.legal_agree_button),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
