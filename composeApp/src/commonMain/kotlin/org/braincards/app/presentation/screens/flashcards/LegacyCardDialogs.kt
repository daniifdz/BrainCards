package org.braincards.app.presentation.screens.flashcards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import braincards.composeapp.generated.resources.*
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.presentation.components.form.InitialFocus
import org.braincards.app.presentation.components.form.formField
import org.braincards.app.presentation.components.form.rememberFormFocus
import org.jetbrains.compose.resources.stringResource

// PROVISIONAL (6b.1/6b.2): los dialogos de tarjeta de siempre, movidos aqui sin cambiar su diseno. Se sustituyen en 6b.3
// (errores por campo y selector de tema). Unica diferencia: el boton primario (y Ctrl/Cmd+Enter) no envian con el
// anverso o el reverso en blanco, para no depender de un error de campo que aun no se dibuja.

@Composable
fun AddFlashcardDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    val focus = rememberFormFocus(2)
    InitialFocus(focus)
    val canSubmit = q.isNotBlank() && a.isNotBlank()
    val submit = { if (canSubmit) onConfirm(q, a) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.new_card)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    q, { q = it },
                    label = { Text(stringResource(Res.string.question_label)) },
                    modifier = Modifier.formField(focus, 0, multiline = true) { submit() }
                )
                OutlinedTextField(
                    a, { a = it },
                    label = { Text(stringResource(Res.string.answer_label)) },
                    modifier = Modifier.formField(focus, 1, multiline = true) { submit() }
                )
            }
        },
        confirmButton = {
            Button({ submit() }, enabled = canSubmit) { Text(stringResource(Res.string.confirm_add)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(Res.string.cancel)) } }
    )
}

@Composable
fun EditFlashcardDialog(
    flashcard: Flashcard,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var q by remember { mutableStateOf(flashcard.question) }
    var a by remember { mutableStateOf(flashcard.answer) }
    val focus = rememberFormFocus(2)
    InitialFocus(focus)
    val canSubmit = q.isNotBlank() && a.isNotBlank()
    val submit = { if (canSubmit) onConfirm(q, a) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    q, { q = it },
                    label = { Text(stringResource(Res.string.question_label)) },
                    modifier = Modifier.formField(focus, 0, multiline = true) { submit() }
                )
                OutlinedTextField(
                    a, { a = it },
                    label = { Text(stringResource(Res.string.answer_label)) },
                    modifier = Modifier.formField(focus, 1, multiline = true) { submit() }
                )
            }
        },
        confirmButton = {
            Button({ submit() }, enabled = canSubmit) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(Res.string.cancel)) } }
    )
}
