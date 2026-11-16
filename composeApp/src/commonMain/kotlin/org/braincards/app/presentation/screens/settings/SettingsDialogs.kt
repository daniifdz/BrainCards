package org.braincards.app.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import braincards.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Progreso modal NO descartable (ni atras ni tocar fuera): comprobacion previa, cierre de sesion y borrado de cuenta. */
@Composable
internal fun SettingsProgressDialog(title: String, hint: String?) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (hint != null) {
                        Text(hint, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CheckingPendingDialog() = SettingsProgressDialog(
    title = stringResource(Res.string.sign_out_checking),
    hint = stringResource(Res.string.sign_out_checking_hint)
)

@Composable
internal fun SigningOutDialog() = SettingsProgressDialog(
    title = stringResource(Res.string.sign_out_progress),
    hint = stringResource(Res.string.sign_out_progress_hint)
)

@Composable
internal fun DeletingAccountDialog() = SettingsProgressDialog(
    title = stringResource(Res.string.delete_account_progress),
    hint = stringResource(Res.string.sign_out_progress_hint)
)

/** "Tienes N cambios sin sincronizar que se perderan": se cierra sesion borrando esos cambios, o se cancela. */
@Composable
internal fun SignOutLossDialog(unsyncedChanges: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(Res.plurals.sign_out_loss_title, unsyncedChanges, unsyncedChanges)) },
        text = { Text(stringResource(Res.string.sign_out_loss_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.sign_out_loss_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

@Composable
internal fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_account_dialog_title)) },
        text = { Text(stringResource(Res.string.delete_account_dialog_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.delete_account_dialog_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}
