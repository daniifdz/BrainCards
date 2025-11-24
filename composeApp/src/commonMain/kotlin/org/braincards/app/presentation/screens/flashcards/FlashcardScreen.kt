package org.braincards.app.presentation.screens.flashcards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.braincards.app.domain.models.Flashcard
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    deckId: Int,
    onBackClick: () -> Unit,
    viewModel: FlashCardViewModel = koinViewModel()
) {
    LaunchedEffect(deckId) {
        viewModel.loadFlashcards(deckId)
    }

    val flashcards by viewModel.flashcards.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flashcards") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Flashcard")
            }
        }
    ) { paddingValues ->
        
        if (flashcards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No cards yet. Add one!", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(flashcards) { card ->
                    FlashcardItem(card)
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
    }
}

@Composable
fun FlashcardItem(flashcard: Flashcard) {
    Card(elevation = CardDefaults.cardElevation(4.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(text = "Q: ${flashcard.question}", style = MaterialTheme.typography.titleMedium)
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
        onDismissRequest = onDismiss,
        title = { Text("New Flashcard") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question") }
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Answer") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (question.isNotBlank() && answer.isNotBlank()) onConfirm(question, answer) }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}