package org.braincards.app.presentation.screens.decks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.decks_card_count
import braincards.composeapp.generated.resources.decks_menu_delete
import braincards.composeapp.generated.resources.decks_menu_edit
import braincards.composeapp.generated.resources.decks_row_a11y
import braincards.composeapp.generated.resources.decks_row_menu
import org.braincards.app.domain.models.DeckSummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Fila de la Lista de mazos: icono + nombre + "N tarjetas" (handoff 5.1b). Tocar la fila abre el mazo.
 *
 * El acceso a Editar / Eliminar es PROVISIONAL (menu de tres puntos, como antes): el diseno aun decide como debe
 * accederse. Todo ese acceso vive en [DeckRowActions]: para cambiarlo basta con sustituir ese componente.
 */
@Composable
fun DeckRow(
    summary: DeckSummary,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = summary.deck.name
    val count = pluralStringResource(Res.plurals.decks_card_count, summary.cardCount, summary.cardCount)
    val rowDescription = stringResource(Res.string.decks_row_a11y, name, count)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
            DeckRowActions(deckName = name, onEdit = onEdit, onDelete = onDelete)
        }
    }
}

/** Acceso provisional a Editar / Eliminar de una fila (menu ⋮). Punto unico a sustituir cuando el diseno lo decida. */
@Composable
private fun DeckRowActions(deckName: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(end = 4.dp)) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.decks_row_menu, deckName)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.decks_menu_edit)) },
                onClick = {
                    expanded = false
                    onEdit()
                },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.decks_menu_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
        }
    }
}
