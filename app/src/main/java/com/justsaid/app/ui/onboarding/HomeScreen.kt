package com.justsaid.app.ui.onboarding

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.justsaid.app.R
import com.justsaid.app.telecom.DialerRole

/** Landing screen: setup cards plus the two Phase 5 destinations (history, settings). */
@Composable
fun HomeScreen(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        DefaultDialerCard()
        CallPermissionsCard()

        val historyDesc = stringResource(R.string.history_open_content_desc)
        Button(
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
        Button(
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

/**
 * One-tap runtime permission request for a non-technical user. Being the default dialer
 * auto-grants these on some OEMs, but we must still ask on devices/flows where it does not.
 * Denial degrades gracefully (no crash): capture simply won't engage until granted.
 */
@Composable
private fun CallPermissionsCard() {
    val context = LocalContext.current
    var allGranted by remember { mutableStateOf(hasAllCallPermissions(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        allGranted = hasAllCallPermissions(context)
    }

    if (allGranted) {
        Text(
            text = stringResource(R.string.home_permissions_active),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    } else {
        Text(
            text = stringResource(R.string.home_permissions_prompt),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        val buttonDesc = stringResource(R.string.home_permissions_content_desc)
        Button(
            onClick = { launcher.launch(requiredCallPermissions()) },
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

/** Runtime (dangerous) permissions JustSaid needs to capture calls and identify callers. */
private fun requiredCallPermissions(): Array<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.ANSWER_PHONE_CALLS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun hasAllCallPermissions(context: Context): Boolean =
    requiredCallPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Recovery affordance: lets the user (re)make JustSaid the default phone app if they declined
 * during first launch — without it, the telecom framework never binds our InCallService and no
 * calls can be captured. Shows a confirmation instead once the role is held.
 */
@Composable
private fun DefaultDialerCard() {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(DialerRole.isDefault(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // Re-check regardless of resultCode: some OEM role dialogs report CANCELED even on grant.
        isDefault = DialerRole.isDefault(context)
    }

    if (isDefault) {
        Text(
            text = stringResource(R.string.home_dialer_active),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    } else {
        Text(
            text = stringResource(R.string.home_dialer_prompt),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        val buttonDesc = stringResource(R.string.home_set_dialer_content_desc)
        Button(
            onClick = {
                val intent = DialerRole.requestIntent(context)
                if (intent != null) launcher.launch(intent) else isDefault = DialerRole.isDefault(context)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics { contentDescription = buttonDesc },
        ) {
            Text(
                text = stringResource(R.string.home_set_dialer_button),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
