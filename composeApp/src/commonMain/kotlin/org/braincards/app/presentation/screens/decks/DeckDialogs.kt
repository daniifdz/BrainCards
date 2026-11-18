package org.braincards.app.presentation.screens.decks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.withFrameNanos
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.cancel
import braincards.composeapp.generated.resources.create
import braincards.composeapp.generated.resources.decks_card_count
import braincards.composeapp.generated.resources.decks_delete_body
import braincards.composeapp.generated.resources.decks_delete_body_empty
import braincards.composeapp.generated.resources.decks_delete_confirm
import braincards.composeapp.generated.resources.decks_delete_title
import braincards.composeapp.generated.resources.decks_dialog_edit_title
import braincards.composeapp.generated.resources.decks_dialog_new_title
import braincards.composeapp.generated.resources.decks_name_blank
import braincards.composeapp.generated.resources.decks_name_duplicate
import braincards.composeapp.generated.resources.decks_name_label
import braincards.composeapp.generated.resources.decks_name_placeholder
import braincards.composeapp.generated.resources.save
import org.braincards.app.domain.usecase.Decks.DeckNameError
import org.braincards.app.presentation.components.form.FormTextField
import org.braincards.app.presentation.components.form.formField
import org.braincards.app.presentation.components.form.rememberFormFocus
import org.braincards.app.presentation.screens.flashcards.shouldShowBlankError
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Dialogo de crear / editar mazo: un solo campo, "Nombre del mazo" (el formulario no tiene descripcion).
 *
 * Teclado (handoff v4.11): foco inicial en el nombre (tambien al editar); Tab en orden natural nombre -> Cancelar ->
 * boton primario; Enter envia (solo con el nombre no vacio); Esc cierra sin guardar (onDismissRequest del Dialog).
 *
 * El boton primario se deshabilita SOLO con el nombre en blanco (o mientras se guarda), nunca por [error]: con
 * texto escrito queda habilitado y el error de campo se ve bajo el campo, en rojo y con borde rojo.
 */
/**
 * Error de campo que muestra el dialogo de mazo. Manda el error que llega del VM (nombre repetido); si no hay, "Escribe
 * un nombre para el mazo." solo cuando el campo esta [touched] (el valor cambio desde que se abrio) y en blanco, nunca
 * en el primer render ([shouldShowBlankError], la misma regla que el dialogo de tema).
 */
internal fun shownDeckNameError(error: DeckNameError?, touched: Boolean, name: String): DeckNameError? =
    error ?: if (shouldShowBlankError(touched, name)) DeckNameError.Blank else null

@Composable
fun DeckNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    showPlaceholder: Boolean,
    error: DeckNameError?,
    saving: Boolean,
    onNameChange: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    // "Tocado" = el valor del campo cambio desde que se abrio el dialogo (no el foco): mismo patron que el dialogo de tema.
    var touched by rememberSaveable { mutableStateOf(false) }
    val focus = rememberFormFocus(1)
    val canSubmit = name.isNotBlank() && !saving
    val shownError = shownDeckNameError(error, touched, name)
    val submit = { if (canSubmit) onConfirm(name) }

    // Foco inicial en el nombre. Se espera un fotograma: dentro de un dialogo el campo aun no esta colocado al componer.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestFocus(0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FormTextField(
                value = name,
                onValueChange = {
                    name = it
                    touched = true
                    onNameChange()
                },
                label = stringResource(Res.string.decks_name_label),
                placeholder = if (showPlaceholder) stringResource(Res.string.decks_name_placeholder) else null,
                error = when (shownError) {
                    DeckNameError.Duplicate -> stringResource(Res.string.decks_name_duplicate)
                    DeckNameError.Blank -> stringResource(Res.string.decks_name_blank)
                    null -> null
                },
                enabled = !saving,
                imeAction = ImeAction.Done,
                onImeAction = { submit() },
                modifier = Modifier.fillMaxWidth().formField(focus, 0, onSubmit = { submit() })
            )
        },
        // Orden de Tab: campo -> Cancelar -> primario (el AlertDialog coloca dismissButton antes que confirmButton).
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(Res.string.cancel)) }
        },
        confirmButton = {
            Button(onClick = { submit() }, enabled = canSubmit) { Text(confirmLabel) }
        }
    )
}

@Composable
fun CreateDeckDialog(error: DeckNameError?, saving: Boolean, onNameChange: () -> Unit, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    DeckNameDialog(
        title = stringResource(Res.string.decks_dialog_new_title),
        confirmLabel = stringResource(Res.string.create),
        initialName = "",
        showPlaceholder = true,
        error = error,
        saving = saving,
        onNameChange = onNameChange,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun EditDeckDialog(currentName: String, error: DeckNameError?, saving: Boolean, onNameChange: () -> Unit, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    DeckNameDialog(
        title = stringResource(Res.string.decks_dialog_edit_title),
        confirmLabel = stringResource(Res.string.save),
        initialName = currentName,
        showPlaceholder = false,
        error = error,
        saving = saving,
        onNameChange = onNameChange,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * Confirmacion de eliminar un mazo (borra tambien sus tarjetas, sin deshacer). Por seguridad el foco inicial va en
 * "Cancelar", no en "Eliminar": un Enter por reflejo no borra nada. Esc cierra sin eliminar.
 */
@Composable
fun DeleteDeckDialog(deckName: String, cardCount: Int, saving: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { cancelFocus.requestFocus() }
    }

    val countPhrase = pluralStringResource(Res.plurals.decks_card_count, cardCount, cardCount)
    val body = when (cardCount) {
        0 -> stringResource(Res.string.decks_delete_body_empty)
        else -> pluralStringResource(Res.plurals.decks_delete_body, cardCount, countPhrase)
    }
    // El recuento ("128 tarjetas") va en negrita, como en el mockup; con 1 tarjeta el texto no lo repite.
    val bodyText = buildAnnotatedString {
        val start = if (cardCount > 1) body.indexOf(countPhrase) else -1
        if (start < 0) {
            append(body)
        } else {
            append(body.substring(0, start))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(countPhrase) }
            append(body.substring(start + countPhrase.length))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Res.string.decks_delete_title, deckName)) },
        text = { Text(bodyText) },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.focusRequester(cancelFocus)) {
                Text(stringResource(Res.string.cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text(stringResource(Res.string.decks_delete_confirm)) }
        }
    )
}
