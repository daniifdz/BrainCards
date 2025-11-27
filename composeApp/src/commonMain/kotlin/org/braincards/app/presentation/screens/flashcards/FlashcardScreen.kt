package org.braincards.app.presentation.screens.flashcards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
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
        viewModel.loadTopics(deckId)
    }

    val flashcards by viewModel.flashcards.collectAsState()
    val topics by viewModel.topics.collectAsState()

    val selectedTopicId by viewModel.selectedTopicId.collectAsState()

    var showAddMenu by remember { mutableStateOf(false) } // Menú del botón +

    var showAddCardDialog by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<Flashcard?>(null) }
    var cardToEdit by remember { mutableStateOf<Flashcard?>(null) }

    var showAddTopicDialog by remember { mutableStateOf(false) }
    var topicToDelete by remember { mutableStateOf<Topic?>(null) }
    var topicToEdit by remember { mutableStateOf<Topic?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contenido del Mazo") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedTopicId != null) {
                            viewModel.selectTopic(null)
                        } else {
                            onBackClick()
                        }
                    }) {
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
            // Botón +
            Box {
                FloatingActionButton(onClick = { showAddMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir")
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Nueva Flashcard") },
                        onClick = {
                            showAddMenu = false
                            showAddCardDialog = true
                        },
                        leadingIcon = { Icon(Icons.Default.NoteAdd, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Nuevo Tema") },
                        onClick = {
                            showAddMenu = false
                            showAddTopicDialog = true
                        },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }
                    )
                }
            }
        }
    ) { paddingValues ->

        if (flashcards.isEmpty() && topics.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Mazo vacío. ¡Añade temas o flashcards!",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            if (selectedTopicId == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    //topics
                    if (topics.isNotEmpty()) {
                        item {
                            Text(
                                "Temas",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(topics) { topic ->
                            TopicItem(
                                topic = topic,
                                onClick = { viewModel.selectTopic(topic.id) },
                                onEditClick = { topicToEdit = topic },
                                onDeleteClick = { topicToDelete = topic }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    //sin tema las flashcards
                    val generalCards = flashcards.filter { it.topicId == null }

                    if (generalCards.isNotEmpty()) {
                        item {
                            Text(
                                "Flashcards Generales",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(generalCards) { card ->
                            FlashcardItem(
                                flashcard = card,
                                onEditClick = { cardToEdit = card },
                                onDeleteClick = { cardToDelete = card }
                            )
                        }
                    }
                }
            } else {
                val cardsInTopic = flashcards.filter { it.topicId == selectedTopicId }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (cardsInTopic.isEmpty()) {
                        item { Text("No hay flashcards en este tema. ¡Añade la primera!") }
                    } else {
                        items(cardsInTopic) { card ->
                            FlashcardItem(
                                flashcard = card,
                                onEditClick = { cardToEdit = it },
                                onDeleteClick = { cardToDelete = it }
                            )
                        }
                    }
                }

            }

        }

        // flashcard dialogos


        if (showAddCardDialog) {
            AddFlashcardDialog(
                onDismiss = { showAddCardDialog = false },
                onConfirm = { q, a ->
                    viewModel.addFlashcard(deckId, q, a, selectedTopicId)
                    showAddCardDialog = false
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
            ConfirmDeleteDialog(
                title = "¿Borrar Flashcard?",
                text = "Se eliminará permanentemente.",
                onConfirm = {
                    viewModel.deleteFlashcard(card)
                    cardToDelete = null
                },
                onDismiss = { cardToDelete = null }
            )
        }

        // topicdialogos

        if (showAddTopicDialog) {
            AddTopicDialog(
                onDismiss = { showAddTopicDialog = false },
                onConfirm = { name ->
                    viewModel.createTopic(deckId, name)
                    showAddTopicDialog = false
                }
            )
        }

        topicToEdit?.let { topic ->
            EditTopicDialog(
                topic = topic,
                onDismiss = { topicToEdit = null },
                onConfirm = { name ->
                    viewModel.updateTopic(topic, name)
                    topicToEdit = null
                }
            )
        }

        topicToDelete?.let { topic ->
            ConfirmDeleteDialog(
                title = "¿Borrar Tema?",
                text = "Se borrará el tema '${topic.name}' Y TODAS SUS FLASHCARDS.",
                onConfirm = {
                    viewModel.deleteTopic(topic)
                    topicToDelete = null
                },
                onDismiss = { topicToDelete = null }
            )
        }
    }
}


@Composable
fun TopicItem(
    topic: Topic,
    onClick: () -> Unit,
    onEditClick: (Topic) -> Unit,
    onDeleteClick: (Topic) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = topic.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        onClick = { showMenu = false; onEditClick(topic) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) })
                    DropdownMenuItem(
                        text = { Text("Borrar") },
                        onClick = { showMenu = false; onDeleteClick(topic) },
                        leadingIcon = { Icon(Icons.Default.Delete, null) })
                }
            }
        }
    }
}

// --- DIÁLOGOS REUTILIZABLES ---

@Composable
fun ConfirmDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Borrar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun AddTopicDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Tema") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del Tema") },
                singleLine = true
            )
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Crear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun EditTopicDialog(topic: Topic, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(topic.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Tema") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}


@Composable
fun FlashcardItem(
    flashcard: Flashcard,
    onEditClick: (Flashcard) -> Unit,
    onDeleteClick: (Flashcard) -> Unit
) {

    var showMenu by remember { mutableStateOf(false) }
    Card(elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Q: ${flashcard.question}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            onClick = { showMenu = false; onEditClick(flashcard) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem(
                            text = { Text("Borrar") },
                            onClick = { showMenu = false; onDeleteClick(flashcard) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(text = "A: ${flashcard.answer}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AddFlashcardDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Nueva Flashcard") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    question,
                    onValueChange = { question = it },
                    label = { Text("Pregunta") }); OutlinedTextField(
                answer,
                onValueChange = { answer = it },
                label = { Text("Respuesta") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (question.isNotBlank() && answer.isNotBlank()) onConfirm(
                    question,
                    answer
                )
            }) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
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
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Editar Flashcard") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("Pregunta") }); OutlinedTextField(
                value = a,
                onValueChange = { a = it },
                label = { Text("Respuesta") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (q.isNotBlank() && a.isNotBlank()) onConfirm(
                    q,
                    a
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}