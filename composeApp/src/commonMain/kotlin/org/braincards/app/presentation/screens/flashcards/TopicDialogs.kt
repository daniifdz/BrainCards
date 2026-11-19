package org.braincards.app.presentation.screens.flashcards

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.cancel
import braincards.composeapp.generated.resources.create
import braincards.composeapp.generated.resources.deckcontent_card_count
import braincards.composeapp.generated.resources.deckcontent_card_delete_body
import braincards.composeapp.generated.resources.deckcontent_card_delete_title
import braincards.composeapp.generated.resources.deckcontent_delete_confirm
import braincards.composeapp.generated.resources.deckcontent_topic_delete_body
import braincards.composeapp.generated.resources.deckcontent_topic_delete_body_empty
import braincards.composeapp.generated.resources.deckcontent_topic_delete_title
import braincards.composeapp.generated.resources.deckcontent_topic_dialog_edit
import braincards.composeapp.generated.resources.deckcontent_topic_dialog_new
import braincards.composeapp.generated.resources.deckcontent_topic_name_blank
import braincards.composeapp.generated.resources.deckcontent_topic_name_duplicate
import braincards.composeapp.generated.resources.deckcontent_topic_name_label
import braincards.composeapp.generated.resources.deckcontent_topic_name_placeholder
import braincards.composeapp.generated.resources.save
import org.braincards.app.domain.usecase.Topics.TopicNameError
import org.braincards.app.presentation.components.form.FormTextField
import org.braincards.app.presentation.components.form.InitialFocus
import org.braincards.app.presentation.components.form.formField
import org.braincards.app.presentation.components.form.rememberFormFocus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * ¿Hay que mostrar el error «campo vacío»? Solo si el usuario ya cambió el valor del campo desde que se abrió el diálogo
 * ([touched], por cambio de valor, no por foco) y ahora está en blanco. Un diálogo recién abierto con el campo vacío no muestra
 * error; el botón primario sigue deshabilitado mientras el campo está vacío.
 */
internal fun shouldShowBlankError(touched: Boolean, value: String): Boolean = touched && value.isBlank()

/**
 * Diálogo de crear / editar tema: un solo campo, «Nombre del tema».
 *
 * Teclado: foco inicial en el nombre (también al editar); Enter envía (solo con el nombre no vacío); Tab en orden nombre ->
 * Cancelar -> primario; Esc cierra sin guardar (onDismissRequest del Dialog). El botón primario se deshabilita solo con el
 * nombre en blanco (o mientras se guarda), nunca por [error].
 */
@Composable
fun TopicNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    showPlaceholder: Boolean,
    error: TopicNameError?,
    saving: Boolean,
    onNameChange: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var touched by rememberSaveable { mutableStateOf(false) }
    val focus = rememberFormFocus(1)
    InitialFocus(focus)
    val canSubmit = name.isNotBlank() && !saving
    val submit = { if (canSubmit) onConfirm(name) }
    val shownError = error ?: if (shouldShowBlankError(touched, name)) TopicNameError.Blank else null

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
                label = stringResource(Res.string.deckcontent_topic_name_label),
                placeholder = if (showPlaceholder) stringResource(Res.string.deckcontent_topic_name_placeholder) else null,
                error = when (shownError) {
                    TopicNameError.Duplicate -> stringResource(Res.string.deckcontent_topic_name_duplicate)
                    TopicNameError.Blank -> stringResource(Res.string.deckcontent_topic_name_blank)
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
fun CreateTopicDialog(
    error: TopicNameError?, saving: Boolean, onNameChange: () -> Unit,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    TopicNameDialog(
        title = stringResource(Res.string.deckcontent_topic_dialog_new),
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
fun EditTopicNameDialog(
    currentName: String, error: TopicNameError?, saving: Boolean, onNameChange: () -> Unit,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    TopicNameDialog(
        title = stringResource(Res.string.deckcontent_topic_dialog_edit),
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
 * Confirmacion destructiva (papelera, texto, Cancelar / Eliminar). Por seguridad el foco inicial va en "Cancelar", no
 * en "Eliminar": un Enter por reflejo no borra nada. Esc cierra sin eliminar.
 */
@Composable
fun DestructiveConfirmDialog(
    title: String,
    body: AnnotatedString,
    saving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancelFocus = remember { FocusRequester() }
    InitialFocus(cancelFocus)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = { Text(body) },
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
            ) { Text(stringResource(Res.string.deckcontent_delete_confirm)) }
        }
    )
}

/** Eliminar un tema BORRA tambien sus [cardCount] tarjetas (el recuento va en negrita; con 1 tarjeta no se repite). */
@Composable
fun DeleteTopicDialog(topicName: String, cardCount: Int, saving: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val countPhrase = pluralStringResource(Res.plurals.deckcontent_card_count, cardCount, cardCount)
    val body = when (cardCount) {
        0 -> stringResource(Res.string.deckcontent_topic_delete_body_empty)
        else -> pluralStringResource(Res.plurals.deckcontent_topic_delete_body, cardCount, countPhrase)
    }
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
    DestructiveConfirmDialog(
        title = stringResource(Res.string.deckcontent_topic_delete_title, topicName),
        body = bodyText,
        saving = saving,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun DeleteCardDialog(saving: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    DestructiveConfirmDialog(
        title = stringResource(Res.string.deckcontent_card_delete_title),
        body = AnnotatedString(stringResource(Res.string.deckcontent_card_delete_body)),
        saving = saving,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
