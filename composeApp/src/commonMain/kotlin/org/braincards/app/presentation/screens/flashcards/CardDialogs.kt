package org.braincards.app.presentation.screens.flashcards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.cancel
import braincards.composeapp.generated.resources.create
import braincards.composeapp.generated.resources.deckcontent_card_answer_blank
import braincards.composeapp.generated.resources.deckcontent_card_answer_label
import braincards.composeapp.generated.resources.deckcontent_card_answer_placeholder
import braincards.composeapp.generated.resources.deckcontent_card_dialog_edit
import braincards.composeapp.generated.resources.deckcontent_card_dialog_new
import braincards.composeapp.generated.resources.deckcontent_card_question_blank
import braincards.composeapp.generated.resources.deckcontent_card_question_label
import braincards.composeapp.generated.resources.deckcontent_card_question_placeholder
import braincards.composeapp.generated.resources.deckcontent_card_topic_label
import braincards.composeapp.generated.resources.deckcontent_topic_none
import braincards.composeapp.generated.resources.save
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.usecase.Flashcards.CardField
import org.braincards.app.presentation.components.form.InitialFocus
import org.braincards.app.presentation.components.form.formField
import org.braincards.app.presentation.components.form.rememberFormFocus
import org.jetbrains.compose.resources.stringResource

/**
 * Diálogo de crear / editar tarjeta: anverso, reverso y selector de tema.
 *
 * Teclado: foco inicial en el anverso (también al editar); Tab en orden natural anverso -> reverso -> selector -> Cancelar ->
 * primario (el selector está en el árbol tras los dos campos y antes de los botones: `formField` ya hace `moveFocus(Next)`
 * desde el último campo, sin tocar `formKeyAction`); Enter inserta salto de línea; Ctrl/Cmd+Enter envía desde cualquiera de
 * los dos campos; Esc cierra sin guardar (onDismissRequest del Dialog).
 *
 * El botón primario se deshabilita con cualquiera de los dos campos en blanco (o mientras se guarda). El error de campo vacío
 * solo sale si el usuario ya cambió el valor de ese campo ([shouldShowBlankError]) o si lo publica el VM ([errors]). El tema
 * que se guarda pasa por [resolveTopicSelection]: si el elegido se borró con el diálogo abierto, cae a «Sin tema».
 */
@Composable
fun CardDialog(
    title: String,
    confirmLabel: String,
    initialQuestion: String,
    initialAnswer: String,
    initialTopicId: String?,
    showPlaceholders: Boolean,
    topicOptions: List<Topic>,
    errors: Set<CardField>,
    saving: Boolean,
    onFieldChange: () -> Unit,
    onConfirm: (question: String, answer: String, topicId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var question by rememberSaveable { mutableStateOf(initialQuestion) }
    var answer by rememberSaveable { mutableStateOf(initialAnswer) }
    var selectedTopicId by rememberSaveable { mutableStateOf(initialTopicId) }
    var questionTouched by rememberSaveable { mutableStateOf(false) }
    var answerTouched by rememberSaveable { mutableStateOf(false) }
    val focus = rememberFormFocus(2)
    InitialFocus(focus)

    val topicId = resolveTopicSelection(selectedTopicId, topicOptions)
    val canSubmit = question.isNotBlank() && answer.isNotBlank() && !saving
    val submit = { if (canSubmit) onConfirm(question, answer, topicId) }
    val showQuestionError = CardField.Question in errors || shouldShowBlankError(questionTouched, question)
    val showAnswerError = CardField.Answer in errors || shouldShowBlankError(answerTouched, answer)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = {
                        question = it
                        questionTouched = true
                        onFieldChange()
                    },
                    label = { Text(stringResource(Res.string.deckcontent_card_question_label)) },
                    placeholder = if (showPlaceholders) {
                        { Text(stringResource(Res.string.deckcontent_card_question_placeholder)) }
                    } else null,
                    isError = showQuestionError,
                    supportingText = if (showQuestionError) {
                        { Text(stringResource(Res.string.deckcontent_card_question_blank)) }
                    } else null,
                    enabled = !saving,
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().formField(focus, 0, multiline = true) { submit() }
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = {
                        answer = it
                        answerTouched = true
                        onFieldChange()
                    },
                    label = { Text(stringResource(Res.string.deckcontent_card_answer_label)) },
                    placeholder = if (showPlaceholders) {
                        { Text(stringResource(Res.string.deckcontent_card_answer_placeholder)) }
                    } else null,
                    isError = showAnswerError,
                    supportingText = if (showAnswerError) {
                        { Text(stringResource(Res.string.deckcontent_card_answer_blank)) }
                    } else null,
                    enabled = !saving,
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().formField(focus, 1, multiline = true) { submit() }
                )
                TopicSelector(
                    options = topicOptions,
                    selectedId = topicId,
                    enabled = !saving,
                    onSelect = { selectedTopicId = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        // Orden de Tab: campos -> selector -> Cancelar -> primario (el AlertDialog coloca dismissButton antes que confirmButton).
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(Res.string.cancel)) }
        },
        confirmButton = {
            Button(onClick = { submit() }, enabled = canSubmit) { Text(confirmLabel) }
        }
    )
}

/**
 * Selector de tema: "Sin tema" primero y luego los [options] (ya ordenados por nombre). La seleccion es por id, asi
 * que dos temas con el mismo nombre (llegados por sync) se distinguen solo por su posicion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicSelector(
    options: List<Topic>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val noneLabel = stringResource(Res.string.deckcontent_topic_none)
    val selectedName = options.firstOrNull { it.id == selectedId }?.name ?: noneLabel

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(stringResource(Res.string.deckcontent_card_topic_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TopicOption(label = noneLabel, selected = selectedId == null) {
                onSelect(null)
                expanded = false
            }
            options.forEach { topic ->
                TopicOption(label = topic.name, selected = topic.id == selectedId) {
                    onSelect(topic.id)
                    expanded = false
                }
            }
        }
    }
}

@Composable
private fun TopicOption(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Bold else null
            )
        },
        onClick = onClick,
        trailingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else null
    )
}

@Composable
fun CreateCardDialog(
    initialTopicId: String?,
    topicOptions: List<Topic>,
    errors: Set<CardField>,
    saving: Boolean,
    onFieldChange: () -> Unit,
    onConfirm: (question: String, answer: String, topicId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    CardDialog(
        title = stringResource(Res.string.deckcontent_card_dialog_new),
        confirmLabel = stringResource(Res.string.create),
        initialQuestion = "",
        initialAnswer = "",
        initialTopicId = initialTopicId,
        showPlaceholders = true,
        topicOptions = topicOptions,
        errors = errors,
        saving = saving,
        onFieldChange = onFieldChange,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun EditCardDialog(
    card: Flashcard,
    topicOptions: List<Topic>,
    errors: Set<CardField>,
    saving: Boolean,
    onFieldChange: () -> Unit,
    onConfirm: (question: String, answer: String, topicId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    CardDialog(
        title = stringResource(Res.string.deckcontent_card_dialog_edit),
        confirmLabel = stringResource(Res.string.save),
        initialQuestion = card.question,
        initialAnswer = card.answer,
        initialTopicId = card.topicId,
        showPlaceholders = false,
        topicOptions = topicOptions,
        errors = errors,
        saving = saving,
        onFieldChange = onFieldChange,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
