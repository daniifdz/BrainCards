package org.braincards.app.presentation.screens.flashcards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.braincards.app.domain.models.Flashcard
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    deckId: Int,
    onBackClick: () -> Unit,
    onStudyClick: (Int) -> Unit,
    viewModel: FlashCardViewModel = koinViewModel()
) {
    LaunchedEffect(deckId) {
        viewModel.loadFlashcards(deckId)
    }

    val flashcards by viewModel.flashcards.collectAsState()
    
    // Estados para controlar diálogos
    var showAddDialog by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<Flashcard?>(null) }
    var cardToEdit by remember { mutableStateOf<Flashcard?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flashcards") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (flashcards.isNotEmpty()) {
                        IconButton(onClick = { onStudyClick(deckId) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Estudiar mazo")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Añade una Flashcard")
            }
        }
    ) { paddingValues ->

        if (flashcards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No hay Flashcards todavía. Agregue una!", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(flashcards) { card ->
                    FlashcardItem(
                        flashcard = card,
                        onEditClick = { cardToEdit = card },
                        onDeleteClick = { cardToDelete = card }
                    )
                }
            }
        }


        if (showAddDialog) {
            AddFlashcardDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { q, a ->
                    viewModel.addFlashcard(deckId, q, a)
                    showAddDialog = false
                }
            )
        }

        cardToEdit?.let { card ->
            EditFlashcardDialog(
                flashcard = card,
                onDismiss = { cardToEdit = null },
                onConfirm = { q, a ->
                    viewModel.updateFlashcard(card, q, a)
                    cardToEdit = null
                }
            )
        }

        cardToDelete?.let { card ->
            AlertDialog(
                onDismissRequest = { cardToDelete = null },
                title = { Text("¿Borrar Flashcard?") },
                text = { Text("Esta acción no se puede deshacer.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteFlashcard(card)
                            cardToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Borrar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { cardToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun FlashcardItem(
    flashcard: Flashcard,
    onEditClick: (Flashcard) -> Unit,
    onDeleteClick: (Flashcard) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(elevation = CardDefaults.cardElevation(4.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Pregunta: ${flashcard.question}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            onClick = {
                                showMenu = false
                                onEditClick(flashcard)
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Borrar") },
                            onClick = {
                                showMenu = false
                                onDeleteClick(flashcard)
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Text(text = "Respuesta: ${flashcard.answer}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AddFlashcardDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva Flashcard") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Pregunta") }
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Respuesta") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (question.isNotBlank() && answer.isNotBlank()) onConfirm(question, answer) }
            ) {
                Text("Añadir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun EditFlashcardDialog(
    flashcard: Flashcard,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var question by remember { mutableStateOf(flashcard.question) }
    var answer by remember { mutableStateOf(flashcard.answer) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Flashcard") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Pregunta") }
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Respuesta") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (question.isNotBlank() && answer.isNotBlank()) onConfirm(question, answer) }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}