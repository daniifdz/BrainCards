package org.braincards.app.presentation.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.braincards.app.domain.models.ReviewQuality
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    deckId: Int,
    onBackClick: () -> Unit,
    viewModel: StudyViewModel = koinViewModel()
) {
    // Iniciamos la sesión al entrar
    LaunchedEffect(deckId) {
        viewModel.initSession(deckId)
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Session") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val currentState = state) {
                is StudyState.Loading -> {
                    CircularProgressIndicator()
                }

                is StudyState.Empty -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "🎉",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "All caught up!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "No cards due for review right now.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = onBackClick) {
                            Text("Back to Decks")
                        }
                    }
                }

                is StudyState.Reviewing -> {
                    ReviewCard(
                        state = currentState,
                        onShowAnswer = { viewModel.showAnswer() },
                        onRate = { quality -> viewModel.rateCard(quality) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewCard(
    state: StudyState.Reviewing,
    onShowAnswer: () -> Unit,
    onRate: (ReviewQuality) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween // Contenido arriba, botones abajo
    ) {
        // Tarjeta (Pregunta / Respuesta)
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Question",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.currentCard.question,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                if (state.isAnswerVisible) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

                    Text(
                        text = "Answer",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.currentCard.answer,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Controles (Botones)
        if (!state.isAnswerVisible) {
            Button(
                onClick = onShowAnswer,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("SHOW ANSWER")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DifficultyButton(
                    text = "Again",
                    color = Color(0xFFFF5252), // Rojo
                    modifier = Modifier.weight(1f),
                    onClick = { onRate(ReviewQuality.AGAIN) }
                )
                DifficultyButton(
                    text = "Hard",
                    color = Color(0xFFFFC107), // Ambar
                    modifier = Modifier.weight(1f),
                    onClick = { onRate(ReviewQuality.HARD) }
                )
                DifficultyButton(
                    text = "Good",
                    color = Color(0xFF4CAF50), // Verde
                    modifier = Modifier.weight(1f),
                    onClick = { onRate(ReviewQuality.GOOD) }
                )
                DifficultyButton(
                    text = "Easy",
                    color = Color(0xFF2196F3), // Azul
                    modifier = Modifier.weight(1f),
                    onClick = { onRate(ReviewQuality.EASY) }
                )
            }
        }

        // Contador restante
        Text(
            text = "${state.remainingCount} cards left",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DifficultyButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(text, maxLines = 1)
    }
}