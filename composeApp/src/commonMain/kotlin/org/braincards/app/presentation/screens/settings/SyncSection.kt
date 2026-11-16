package org.braincards.app.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import braincards.composeapp.generated.resources.*
import org.braincards.app.presentation.sync.LastSyncLabel
import org.braincards.app.presentation.sync.SyncDetail
import org.braincards.app.presentation.sync.SyncDetailState
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

private const val DETAIL_SEPARATOR = " · "

/** Seccion "Sincronizacion" de Ajustes: estado detallado (8 estados con sesion) + "Sincronizar ahora". */
@Composable
internal fun SyncSection(detail: SyncDetail, canSyncNow: Boolean, onSyncNow: () -> Unit) {
    val extra = BrainCardsTheme.extraColors
    val scheme = MaterialTheme.colorScheme
    val tint = when {
        detail.canRetry -> scheme.error
        detail.state == SyncDetailState.Synced -> extra.chipTeal
        else -> extra.chipGray
    }
    val title = stringResource(
        when (detail.state) {
            SyncDetailState.Syncing -> Res.string.sync_pill_syncing
            SyncDetailState.Synced -> Res.string.sync_pill_synced
            SyncDetailState.NeverSynced -> Res.string.sync_pill_never
            SyncDetailState.Offline -> Res.string.sync_pill_offline
            SyncDetailState.ErrorLocal -> Res.string.sync_pill_error_local
            SyncDetailState.ErrorRejected -> Res.string.sync_pill_error_rejected
            SyncDetailState.ErrorPushPartial -> Res.string.sync_pill_error_push_partial
            SyncDetailState.ErrorFailed -> Res.string.sync_pill_error_failed
        }
    )
    val parts = buildList {
        detail.lastSync?.let { add(stringResource(Res.string.sync_detail_last, lastSyncText(it))) }
        when (detail.state) {
            SyncDetailState.NeverSynced -> add(stringResource(Res.string.sync_detail_never))
            SyncDetailState.Offline -> add(stringResource(Res.string.sync_detail_offline))
            SyncDetailState.ErrorLocal -> add(stringResource(Res.string.sync_detail_error_local))
            SyncDetailState.ErrorPushPartial -> add(stringResource(Res.string.sync_detail_error_push_partial))
            SyncDetailState.ErrorFailed -> add(stringResource(Res.string.sync_detail_error_failed))
            else -> Unit // Syncing, Synced y ErrorRejected: sin texto fijo extra
        }
        detail.pending?.let { add(pluralStringResource(Res.plurals.sync_pending_changes, it, it)) }
    }

    Text(
        text = stringResource(Res.string.settings_sync_title),
        style = MaterialTheme.typography.titleMedium,
        color = scheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Column {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = if (parts.isEmpty()) null else {
                { Text(parts.joinToString(DETAIL_SEPARATOR)) }
            },
            leadingContent = {
                if (detail.state == SyncDetailState.Syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = tint)
                } else {
                    Icon(
                        imageVector = when (detail.state) {
                            SyncDetailState.Synced -> Icons.Default.CloudDone
                            SyncDetailState.NeverSynced -> Icons.Default.Schedule
                            SyncDetailState.Offline -> Icons.Default.WifiOff
                            else -> Icons.Default.ErrorOutline
                        },
                        contentDescription = null,
                        tint = tint
                    )
                }
            },
            trailingContent = if (!detail.canRetry) null else {
                {
                    TextButton(onClick = onSyncNow, enabled = canSyncNow) {
                        Text(stringResource(Res.string.home_retry))
                    }
                }
            }
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.sync_now)) },
            leadingContent = { Icon(Icons.Default.Sync, contentDescription = null) },
            modifier = Modifier.clickable(enabled = canSyncNow, onClick = onSyncNow)
        )
    }
}

@Composable
private fun lastSyncText(label: LastSyncLabel): String = when (label) {
    is LastSyncLabel.Today -> stringResource(Res.string.sync_last_today, label.time)
    is LastSyncLabel.Yesterday -> stringResource(Res.string.sync_last_yesterday, label.time)
    is LastSyncLabel.Date -> {
        val months = stringArrayResource(Res.array.sync_months_short)
        stringResource(Res.string.sync_last_date, label.day, months[label.month - 1], label.time)
    }
}
