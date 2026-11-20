package org.braincards.app.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import braincards.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Dirección a la que escribe «Enviar comentarios». Es la única vez que aparece en el código. */
const val FEEDBACK_EMAIL = "braincards.soporte@proton.me"

/**
 * Aviso de «Enviar comentarios» cuando no se pudo abrir ningún cliente de correo: muestra la dirección (seleccionable, por si el
 * portapapeles falla) y un botón para copiarla.
 */
@Composable
internal fun FeedbackUnavailableDialog(address: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.feedback_unavailable_title)) },
        text = { FeedbackUnavailableContent(address) },
        confirmButton = { TextButton(onClick = onCopy) { Text(stringResource(Res.string.feedback_copy_address)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feedback_close)) } }
    )
}

@Composable
internal fun FeedbackUnavailableContent(address: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.feedback_unavailable_body))
        SelectionContainer {
            Text(address, style = MaterialTheme.typography.titleMedium)
        }
    }
}
