package org.braincards.app.presentation.screens.flashcards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import braincards.composeapp.generated.resources.*
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    deckId: String,
    onBackClick: () -> Unit,
    onStudyClick: (String) -> Unit,
    flashcardViewModel: FlashCardViewModel = koinViewModel(),
    topicViewModel: TopicVM = koinViewModel()
) {
    LaunchedEffect(deckId) {
        flashcardViewModel.loadFlashcards(deckId)
        topicViewModel.loadTopics(deckId)
    }

    val flashcardsState by flashcardViewModel.flashcards.collectAsState()
    val topicsState by topicViewModel.topics.collectAsState()

    val selectedTopicId by topicViewModel.selectedTopicId.collectAsState()
    val searchQuery by flashcardViewModel.searchQuery.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddCardDialog by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<Flashcard?>(null) }
    var cardToEdit by remember { mutableStateOf<Flashcard?>(null) }
    var showAddTopicDialog by remember { mutableStateOf(false) }
    var topicToDelete by remember { mutableStateOf<Topic?>(null) }
    var topicToEdit by remember { mutableStateOf<Topic?>(null) }


    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        flashcardViewModel.errorEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    LaunchedEffect(Unit) {
        topicViewModel.errorEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }


    // Manejo de Estados de Carga y Error
    when {
        flashcardsState is FlashcardsUiState.Loading || topicsState is TopicUiState.Loading -> {
            LoadingIndicator()
            return
        }
        flashcardsState is FlashcardsUiState.Error -> {
            ErrorMessage((flashcardsState as FlashcardsUiState.Error).message)
            return
        }
        topicsState is TopicUiState.Error -> {
            ErrorMessage((topicsState as TopicUiState.Error).message)
            return
        }
    }

    val flashcards = (flashcardsState as FlashcardsUiState.Success).flashcards
    val topics = (topicsState as TopicUiState.Success).topics
    val selectedTopic = topics.find { it.id == selectedTopicId }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },

        topBar = {
            FlashcardTopBar(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                selectedTopicName = selectedTopic?.name,
                onSearchQueryChange = { flashcardViewModel.onSearchQueryChange(it) },
                onCloseSearch = {
                    isSearchActive = false
                    flashcardViewModel.onSearchQueryChange("")
                },
                onOpenSearch = { isSearchActive = true },
                onBackClick = {
                    if (selectedTopicId != null) {
                        topicViewModel.selectTopic(null)
                    } else {
                        onBackClick()
                    }
                },
                onStudyClick = { onStudyClick(deckId) },
                showStudyIcon = flashcards.isNotEmpty() && searchQuery.isEmpty()
            )
        },
        floatingActionButton = {
            FlashcardFab(
                isSearchActive = isSearchActive,
                selectedTopicId = selectedTopicId,
                onAddMenuClick = { showAddMenu = true },
                onAddCardClick = { showAddCardDialog = true },
                showAddMenu = showAddMenu,
                onDismissMenu = { showAddMenu = false },
                onNewCardOption = {
                    showAddMenu = false
                    showAddCardDialog = true
                },
                onNewTopicOption = {
                    showAddMenu = false
                    showAddTopicDialog = true
                }
            )
        }
    ) { paddingValues ->
        FlashcardListContent(
            paddingValues = paddingValues,
            searchQuery = searchQuery,
            selectedTopicId = selectedTopicId,
            flashcards = flashcards,
            topics = topics,
            onTopicClick = {
                topicViewModel.selectTopic(it)
                flashcardViewModel.onSearchQueryChange("")
            },
            onTopicEdit = { topicToEdit = it },
            onTopicDelete = { topicToDelete = it },
            onCardEdit = { cardToEdit = it },
            onCardDelete = { cardToDelete = it }
        )

        // Diálogos
        if (showAddCardDialog) {
            AddFlashcardDialog(
                onDismiss = { showAddCardDialog = false },
                onConfirm = { q, a ->
                    flashcardViewModel.addFlashcard(deckId, q, a, selectedTopicId)
                    showAddCardDialog = false
                }
            )
        }
        cardToEdit?.let { card ->
            EditFlashcardDialog(
                flashcard = card,
                onDismiss = { cardToEdit = null },
                onConfirm = { q, a ->
                    flashcardViewModel.updateFlashcard(card, q, a)
                    cardToEdit = null
                }
            )
        }
        cardToDelete?.let { card ->
            ConfirmDeleteDialog(
                title = stringResource(Res.string.delete_card_title),
                text = stringResource(Res.string.delete_card_text),
                onConfirm = {
                    flashcardViewModel.deleteFlashcard(card)
                    cardToDelete = null
                },
                onDismiss = { cardToDelete = null }
            )
        }
        if (showAddTopicDialog) {
            AddTopicDialog(
                onDismiss = { showAddTopicDialog = false },
                onConfirm = { name ->
                    topicViewModel.createTopic(deckId, name)
                    showAddTopicDialog = false
                }
            )
        }
        topicToEdit?.let { topic ->
            EditTopicDialog(
                topic = topic,
                onDismiss = { topicToEdit = null },
                onConfirm = { name ->
                    topicViewModel.updateTopic(topic, name)
                    topicToEdit = null
                }
            )
        }
        topicToDelete?.let { topic ->
            ConfirmDeleteDialog(
                title = stringResource(Res.string.delete_topic_title),
                text = stringResource(Res.string.delete_topic_text, topic.name),
                onConfirm = {
                    topicViewModel.deleteTopic(topic)
                    topicToDelete = null
                },
                onDismiss = { topicToDelete = null }
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlashcardTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    selectedTopicName: String?,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onOpenSearch: () -> Unit,
    onBackClick: () -> Unit,
    onStudyClick: () -> Unit,
    showStudyIcon: Boolean
) {
    if (isSearchActive) {
        TopAppBar(
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(Res.string.search_hint)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            navigationIcon = {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.close_search))
                }
            },
            actions = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear_text))
                    }
                }
            }
        )
    } else {
        TopAppBar(
            title = {
                Text(selectedTopicName?.let { stringResource(Res.string.topic_title, it) } ?: stringResource(Res.string.deck_content))
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                }
            },
            actions = {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search))
                }
                if (showStudyIcon) {
                    IconButton(onClick = onStudyClick) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(Res.string.study_deck))
                    }
                }
            }
        )
    }
}

@Composable
private fun FlashcardFab(
    isSearchActive: Boolean,
    selectedTopicId: String?,
    onAddMenuClick: () -> Unit,
    onAddCardClick: () -> Unit,
    showAddMenu: Boolean,
    onDismissMenu: () -> Unit,
    onNewCardOption: () -> Unit,
    onNewTopicOption: () -> Unit
) {
    if (!isSearchActive) {
        if (selectedTopicId == null) {
            Box {
                FloatingActionButton(onClick = onAddMenuClick) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add))
                }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.new_card)) },
                        onClick = onNewCardOption,
                        leadingIcon = { Icon(Icons.Default.NoteAdd, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.new_topic)) },
                        onClick = onNewTopicOption,
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }
                    )
                }
            }
        } else {
            FloatingActionButton(onClick = onAddCardClick) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.new_card))
            }
        }
    }
}

@Composable
private fun FlashcardListContent(
    paddingValues: PaddingValues,
    searchQuery: String,
    selectedTopicId: String?,
    flashcards: List<Flashcard>,
    topics: List<Topic>,
    onTopicClick: (String) -> Unit,
    onTopicEdit: (Topic) -> Unit,
    onTopicDelete: (Topic) -> Unit,
    onCardEdit: (Flashcard) -> Unit,
    onCardDelete: (Flashcard) -> Unit
) {
    if (searchQuery.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(flashcards) { card ->
                FlashcardItem(
                    flashcard = card,
                    onEditClick = onCardEdit,
                    onDeleteClick = onCardDelete
                )
            }
        }
    } else {
        if (selectedTopicId == null) {
            RootContent(
                paddingValues = paddingValues,
                topics = topics,
                flashcards = flashcards,
                onTopicClick = onTopicClick,
                onTopicEdit = onTopicEdit,
                onTopicDelete = onTopicDelete,
                onCardEdit = onCardEdit,
                onCardDelete = onCardDelete
            )
        } else {
            TopicContent(
                paddingValues = paddingValues,
                flashcardsInTopic = flashcards.filter { it.topicId == selectedTopicId },
                onCardEdit = onCardEdit,
                onCardDelete = onCardDelete
            )
        }
    }
}

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
        EmptyStateMessage(paddingValues, stringResource(Res.string.empty_deck))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (topics.isNotEmpty()) {
            item { SectionHeader(stringResource(Res.string.temas_header)) }
            items(topics) { topic ->
                TopicItem(
                    topic = topic,
                    onClick = { onTopicClick(topic.id) },
                    onEditClick = onTopicEdit,
                    onDeleteClick = onTopicDelete
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        val generalCards = flashcards.filter { it.topicId == null }
        if (generalCards.isNotEmpty()) {
            item { SectionHeader(stringResource(Res.string.general_cards_header)) }
            items(generalCards) { card ->
                FlashcardItem(
                    flashcard = card,
                    onEditClick = onCardEdit,
                    onDeleteClick = onCardDelete
                )
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
        EmptyStateMessage(paddingValues, stringResource(Res.string.empty_topic))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(flashcardsInTopic) { card ->
            FlashcardItem(
                flashcard = card,
                onEditClick = onCardEdit,
                onDeleteClick = onCardDelete
            )
        }
    }
}

@Composable
private fun EmptyStateMessage(paddingValues: PaddingValues, message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
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
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(16.dp))
                Text(
                    topic.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Box {
                IconButton({ showMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(Res.string.options)) }
                DropdownMenu(showMenu, { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.edit)) },
                        onClick = {
                            showMenu = false
                            onEditClick(topic)
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.delete)) },
                        onClick = {
                            showMenu = false
                            onDeleteClick(topic)
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(Res.string.delete)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(Res.string.cancel)) } }
    )
}

@Composable
fun AddTopicDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.new_topic)) },
        text = {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(stringResource(Res.string.topic_name_label)) },
                singleLine = true
            )
        },
        confirmButton = {
            Button({ onConfirm(name) }) { Text(stringResource(Res.string.create)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(Res.string.cancel)) } }
    )
}

@Composable
fun EditTopicDialog(topic: Topic, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(topic.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit)) },
        text = {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(stringResource(Res.string.name_label)) },
                singleLine = true
            )
        },
        confirmButton = {
            Button({ onConfirm(name) }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(Res.string.cancel)) } }
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
                    "Q: ${flashcard.question}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton({ showMenu = true }, Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                    DropdownMenu(showMenu, { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.edit)) },
                            onClick = {
                                showMenu = false
                                onEditClick(flashcard)
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.delete)) },
                            onClick = {
                                showMenu = false
                                onDeleteClick(flashcard)
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null) }
                        )
                    }
                }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.new_card)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, label = { Text(stringResource(Res.string.question_label)) })
                OutlinedTextField(a, { a = it }, label = { Text(stringResource(Res.string.answer_label)) })
            }
        },
        confirmButton = {
            Button({ onConfirm(q, a) }) { Text(stringResource(Res.string.confirm_add)) }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, label = { Text(stringResource(Res.string.question_label)) })
                OutlinedTextField(a, { a = it }, label = { Text(stringResource(Res.string.answer_label)) })
            }
        },
        confirmButton = {
            Button({ onConfirm(q, a) }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(Res.string.cancel)) } }
    )
}