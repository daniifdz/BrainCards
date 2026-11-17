package org.braincards.app.presentation.screens.flashcards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.deckcontent_card_count
import braincards.composeapp.generated.resources.deckcontent_card_menu
import braincards.composeapp.generated.resources.deckcontent_menu_delete
import braincards.composeapp.generated.resources.deckcontent_menu_edit
import braincards.composeapp.generated.resources.deckcontent_row_a11y
import braincards.composeapp.generated.resources.deckcontent_topic_menu
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.usecase.Topics.TopicWithCount
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val ROW_SHAPE = RoundedCornerShape(14.dp)

/**
 * Fila de un tema: icono + nombre + "N tarjetas" + menu (⋮) + chevron (handoff 5.1c). Tocar la fila (o el chevron) abre
 * el subnivel del tema. Editar / Eliminar viven en [ContentRowActions] (provisional, ver alli).
 */
@Composable
fun TopicRow(
    item: TopicWithCount,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = item.topic.name
    val count = pluralStringResource(Res.plurals.deckcontent_card_count, item.cardCount, item.cardCount)
    val rowDescription = stringResource(Res.string.deckcontent_row_a11y, name, count)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ROW_SHAPE,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Zona principal: UN solo elemento accesible (nombre + recuento) para no leerlo en dos trozos.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics(mergeDescendants = true) { contentDescription = rowDescription }
                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RowChip(background = MaterialTheme.colorScheme.primaryContainer, tint = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = count,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ContentRowActions(
                menuLabel = stringResource(Res.string.deckcontent_topic_menu, name),
                onEdit = onEdit,
                onDelete = onDelete
            )
            // El chevron tambien abre el tema (zona de toque grande) pero no se anuncia aparte: la zona principal ya lo hace.
            Box(
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .clickable(onClick = onClick)
                    .heightIn(min = 48.dp)
                    .padding(end = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Fila de una tarjeta: icono + anverso (+ [topicName] como segunda linea en los resultados de busqueda) + menu (⋮).
 * Tocar la fila abre "Editar tarjeta".
 */
@Composable
fun CardRow(
    card: Flashcard,
    topicName: String?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowDescription = if (topicName == null) {
        card.question
    } else {
        stringResource(Res.string.deckcontent_row_a11y, card.question, topicName)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ROW_SHAPE,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics(mergeDescendants = true) { contentDescription = rowDescription }
                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RowChip(
                    background = MaterialTheme.colorScheme.secondaryContainer,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
                    Text(
                        text = card.question,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (topicName != null) {
                        Text(
                            text = topicName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Box(modifier = Modifier.padding(end = 4.dp)) {
                ContentRowActions(
                    menuLabel = stringResource(Res.string.deckcontent_card_menu, card.question),
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun RowChip(background: Color, tint: Color, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = background, contentColor = tint) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Acceso PROVISIONAL a Editar / Eliminar de una fila (menu ⋮): el diseno no dibuja todavia como acceder (handoff
 * 5.1c solo muestra el chevron del tema y el lapiz de la tarjeta). Punto unico a sustituir cuando el diseno lo decida.
 */
@Composable
private fun ContentRowActions(menuLabel: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = menuLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.deckcontent_menu_edit)) },
                onClick = {
                    expanded = false
                    onEdit()
                },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.deckcontent_menu_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
        }
    }
}
