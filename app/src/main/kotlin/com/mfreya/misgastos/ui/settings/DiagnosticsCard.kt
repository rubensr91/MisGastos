package com.mfreya.misgastos.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mfreya.misgastos.R
import com.mfreya.misgastos.ui.support.NotificationAccess

@Composable
fun DiagnosticsCard(
    accessGranted: Boolean,
    listenerConnected: Boolean,
    batteryUnrestricted: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_section_diagnostics),
                style = MaterialTheme.typography.titleMedium,
            )
            StatusRow(
                ok = accessGranted,
                okText = stringResource(R.string.settings_access_granted),
                failText = stringResource(R.string.settings_access_missing),
            )
            if (accessGranted) {
                StatusRow(
                    ok = listenerConnected,
                    okText = stringResource(R.string.settings_diag_service_ok),
                    failText = stringResource(R.string.settings_diag_service_missing),
                )
                if (!listenerConnected && !NotificationAccess.canRebind()) {
                    // En Android 12 o anterior no hay forma de pedir al sistema que lo
                    // vuelva a enlazar, asi que se avisa en vez de fallar en silencio.
                    Text(
                        text = stringResource(R.string.settings_diag_rebind_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusRow(
                ok = batteryUnrestricted,
                okText = stringResource(R.string.settings_diag_battery_ok),
                failText = stringResource(R.string.settings_diag_battery_restricted),
            )
            if (!accessGranted) {
                TextButton(onClick = { context.openIntentOrFallback(NotificationAccess.directSettingsIntent(context), NotificationAccess.settingsIntent(context)) }) {
                    Text(text = stringResource(R.string.settings_open_access))
                }
            }
            if (!batteryUnrestricted) {
                TextButton(onClick = { context.openIntentOrFallback(NotificationAccess.batteryOptimizationIntent(context)) }) {
                    Text(text = stringResource(R.string.settings_diag_battery_open))
                }
            }
            Text(
                text = stringResource(R.string.settings_diag_suggested),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            AdviceRow(stringResource(R.string.settings_diag_advice_battery))
            AdviceRow(stringResource(R.string.settings_diag_advice_no_data))
        }
    }
}

@Composable
private fun StatusRow(
    ok: Boolean,
    okText: String,
    failText: String,
) {
    val statusColor = if (ok) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ok) Icons.Filled.Check else Icons.Filled.Warning,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (ok) okText else failText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.onSurface else statusColor,
        )
    }
}

@Composable
private fun AdviceRow(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun Context.openIntentOrFallback(intent: Intent?, fallback: Intent? = null) {
    val target = intent ?: fallback ?: return
    runCatching { startActivity(target) }.onFailure {
        runCatching { packageManager.getLaunchIntentForPackage(packageName) }
            .getOrNull()
            ?.let { launcher -> runCatching { startActivity(launcher) } }
    }
}
