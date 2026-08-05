package org.braincards.app.presentation.screens.flashcards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    deckId: String,
    onBackClick: () -> Unit,
    onStudyClick: (String) -> Unit,
    viewModel: FlashCardViewModel = koinViewModel()
) {
    LaunchedEffect(deckId) {
        viewModel.loadFlashcards(deckId)
        viewModel.loadTopics(deckId)
    }

    val flashcards by viewModel.flashcards.collectAsState()
    val topics by viewModel.topics.collectAsState()
    val selectedTopicId by viewModel.selectedTopicId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    //estados
    var isSearchActive by remember { mutableStateOf(false) }

    var showAddMenu by remember { mutableStateOf(false) }
    var showAddCardDialog by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<Flashcard?>(null) }
    var cardToEdit by remember { mutableStateOf<Flashcard?>(null) }
    var showAddTopicDialog by remember { mutableStateOf(false) }
    var topicToDelete by remember { mutableStateOf<Topic?>(null) }
    var topicToEdit by remember { mutableStateOf<Topic?>(null) }

    val selectedTopic = topics.find { it.id == selectedTopicId }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                //busqueda
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            placeholder = { Text("Buscar carta...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.onSearchQueryChange("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar búsqueda")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Borrar texto")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(if (selectedTopic != null) "Tema: ${selectedTopic.name}" else "Contenido del Mazo")
                    },
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
                        // Icono de Búsqueda
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                        // Icono de Estudiar (Solo si hay cartas y no estamos buscando)
                        if (flashcards.isNotEmpty()) {
                            IconButton(onClick = { onStudyClick(deckId) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Estudiar mazo")
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSearchActive) {
                if (selectedTopicId == null) {
                    Box {
                        FloatingActionButton(onClick = { showAddMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Añadir")
                        }
                        DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                            DropdownMenuItem(text = { Text("Nueva Carta") }, onClick = { showAddMenu = false; showAddCardDialog = true }, leadingIcon = { Icon(Icons.Default.NoteAdd, null) })
                            DropdownMenuItem(text = { Text("Nuevo Tema") }, onClick = { showAddMenu = false; showAddTopicDialog = true }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) })
                        }
                    }
                } else {
                    FloatingActionButton(onClick = { showAddCardDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir Carta")
                    }
                }
            }
        }
    ) { paddingValues ->

        // mostramos cartas filtradas)
        if (searchQuery.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(flashcards) { card ->
                    FlashcardItem(
                        flashcard = card,
                        onEditClick = { cardToEdit = it },
                        onDeleteClick = { cardToDelete = it }
                    )
                }
            }
        } else {
            if (selectedTopicId == null) {
                RootContent(
                    paddingValues = paddingValues,
                    topics = topics,
                    flashcards = flashcards,
                    onTopicClick = { topicId -> viewModel.selectTopic(topicId) },
                    onTopicEdit = { topicToEdit = it },
                    onTopicDelete = { topicToDelete = it },
                    onCardEdit = { cardToEdit = it },
                    onCardDelete = { cardToDelete = it }
                )
            } else {
                TopicContent(
                    paddingValues = paddingValues,
                    flashcardsInTopic = flashcards.filter { it.topicId == selectedTopicId },
                    onCardEdit = { cardToEdit = it },
                    onCardDelete = { cardToDelete = it }
                )
            }
        }

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
            EditFlashcardDialog(flashcard = card, onDismiss = { cardToEdit = null }, onConfirm = { q, a -> viewModel.updateFlashcard(card, q, a); cardToEdit = null })
        }
        cardToDelete?.let { card ->
            ConfirmDeleteDialog(title = "¿Borrar Carta?", text = "Se eliminará permanentemente.", onConfirm = { viewModel.deleteFlashcard(card); cardToDelete = null }, onDismiss = { cardToDelete = null })
        }
        if (showAddTopicDialog) {
            AddTopicDialog(onDismiss = { showAddTopicDialog = false }, onConfirm = { name -> viewModel.createTopic(deckId, name); showAddTopicDialog = false })
        }
        topicToEdit?.let { topic ->
            EditTopicDialog(topic = topic, onDismiss = { topicToEdit = null }, onConfirm = { name -> viewModel.updateTopic(topic, name); topicToEdit = null })
        }
        topicToDelete?.let { topic ->
            ConfirmDeleteDialog(title = "¿Borrar Tema?", text = "Se borrará el tema '${topic.name}' Y TODAS SUS CARTAS.", onConfirm = { viewModel.deleteTopic(topic); topicToDelete = null }, onDismiss = { topicToDelete = null })
        }
    }
}



//componentes contenido

@Composable
private fun RootContent(
    paddingValues: PaddingValues,
    topics: List<Topic>,
    flashcards: List<Flashcard>,
    onTopicClick: (String) -> Unit,
    onTopicEdit: (Topic) -> Unit,
    onTopicDelete: (Topic) -> Unit,
    onCardEdit: (Flashcard) -> Unit,
    onCardDelete: (Flashcard) -> Unit
) {
    if (topics.isEmpty() && flashcards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Text("Mazo vacío. ¡Añade temas o cartas!", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (topics.isNotEmpty()) {
            item { Text("Temas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            items(topics) { topic ->
                TopicItem(topic = topic, onClick = { onTopicClick(topic.id) }, onEditClick = onTopicEdit, onDeleteClick = onTopicDelete)
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        val generalCards = flashcards.filter { it.topicId == null }
        if (generalCards.isNotEmpty()) {
            item { Text("Cartas Generales", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            items(generalCards) { card ->
                FlashcardItem(flashcard = card, onEditClick = onCardEdit, onDeleteClick = onCardDelete)
            }
        }
    }
}

@Composable
private fun TopicContent(
    paddingValues: PaddingValues,
    flashcardsInTopic: List<Flashcard>,
    onCardEdit: (Flashcard) -> Unit,
    onCardDelete: (Flashcard) -> Unit
) {
    if (flashcardsInTopic.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Text("No hay cartas en este tema. ¡Añade la primera!", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(flashcardsInTopic) { card ->
            FlashcardItem(flashcard = card, onEditClick = onCardEdit, onDeleteClick = onCardDelete)
        }
    }
}



@Composable
fun TopicItem(topic: Topic, onClick: () -> Unit, onEditClick: (Topic) -> Unit, onDeleteClick: (Topic) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer); Spacer(Modifier.width(16.dp)); Text(topic.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer) }
            Box { IconButton({ showMenu = true }) { Icon(Icons.Default.MoreVert, "Opciones") }; DropdownMenu(showMenu, { showMenu = false }) { DropdownMenuItem({ Text("Editar") }, { showMenu = false; onEditClick(topic) }, leadingIcon = { Icon(Icons.Default.Edit, null) }); DropdownMenuItem({ Text("Borrar") }, { showMenu = false; onDeleteClick(topic) }, leadingIcon = { Icon(Icons.Default.Delete, null) }) } }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Borrar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
fun AddTopicDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nuevo Tema") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Nombre del Tema") }, singleLine = true) }, confirmButton = { Button({ if (name.isNotBlank()) onConfirm(name) }) { Text("Crear") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
fun EditTopicDialog(topic: Topic, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(topic.name) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar Tema") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true) }, confirmButton = { Button({ if (name.isNotBlank()) onConfirm(name) }) { Text("Guardar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}


@Composable
fun FlashcardItem(flashcard: Flashcard, onEditClick: (Flashcard) -> Unit, onDeleteClick: (Flashcard) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Card(elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text("Q: ${flashcard.question}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box { IconButton({ showMenu = true }, Modifier.size(24.dp)) { Icon(Icons.Default.MoreVert, null) }; DropdownMenu(showMenu, { showMenu = false }) { DropdownMenuItem({ Text("Editar") }, { showMenu = false; onEditClick(flashcard) }, leadingIcon = { Icon(Icons.Default.Edit, null) }); DropdownMenuItem({ Text("Borrar") }, { showMenu = false; onDeleteClick(flashcard) }, leadingIcon = { Icon(Icons.Default.Delete, null) }) } }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("A: ${flashcard.answer}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AddFlashcardDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nueva Carta") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(q, { q = it }, label = { Text("Pregunta") }); OutlinedTextField(a, { a = it }, label = { Text("Respuesta") }) } }, confirmButton = { Button({ if (q.isNotBlank() && a.isNotBlank()) onConfirm(q, a) }) { Text("Añadir") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
fun EditFlashcardDialog(flashcard: Flashcard, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var q by remember { mutableStateOf(flashcard.question) }
    var a by remember { mutableStateOf(flashcard.answer) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar Carta") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(q, { q = it }, label = { Text("Pregunta") }); OutlinedTextField(a, { a = it }, label = { Text("Respuesta") }) } }, confirmButton = { Button({ if (q.isNotBlank() && a.isNotBlank()) onConfirm(q, a) }) { Text("Guardar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}