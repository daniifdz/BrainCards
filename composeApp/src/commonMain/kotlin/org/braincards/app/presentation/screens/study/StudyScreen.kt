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
import org.braincards.app.presentation.components.FlipCard
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    deckId: Int,
    onBackClick: () -> Unit,
    viewModel: StudyViewModel = koinViewModel()
) {
    LaunchedEffect(deckId) {
        viewModel.initSession(deckId)
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sesión de estudio") },
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
                            "Estamos al día!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "No hay flashcards pendientes para repasar ahora mismo.",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(onClick = onBackClick) {
                            Text("Volver a los mazos")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(onClick = { viewModel.reviewAllCards(deckId) }) {
                            Text("Repasar todo de todas formas")
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
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Tarjeta que gira (FlipCard ya es una Card visualmente)
        FlipCard(
            isFlipped = state.isAnswerVisible,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            front = {
                // CARA A: PREGUNTA
                CardFaceContent(
                    title = "Pregunta",
                    text = state.currentCard.question,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            back = {
                // CARA B: RESPUESTA
                CardFaceContent(
                    title = "Respuesta",
                    text = state.currentCard.answer,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        )

        // Controles (Botones)
        if (!state.isAnswerVisible) {
            Button(
                onClick = onShowAnswer,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("VER RESPUESTA")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DifficultyButton(
                    text = "Otra vez",
                    color = Color(0xFFFF5252),
                    modifier = Modifier.weight(1f),
                    onClick = { onRate(ReviewQuality.AGAIN) }
                )
                DifficultyButton(
                    text = "Difícil",
                    color = Color(0xFFFFC107),
                    modifier = Modifier.weight(1f),
                    onClick = { onRate(ReviewQuality.HARD) }
                )
                DifficultyButton(
                    text = "Bien",
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f),
                    onClick = { onRate(ReviewQuality.GOOD) }
                )
                DifficultyButton(
                    text = "Muy fácil",
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f),
                    onClick = { onRate(ReviewQuality.EASY) }
                )
            }
        }

        // Contador restante
        Text(
            text = "${state.remainingCount} flashcards restantes",
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

@Composable
fun CardFaceContent(title: String, text: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium, // Un poco más grande
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}