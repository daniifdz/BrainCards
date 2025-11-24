package org.braincards.app

import androidx.compose.animation.Crossfade
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.braincards.app.presentation.screens.decks.DecksScreen
import org.braincards.app.presentation.screens.flashcards.FlashcardScreen
import org.braincards.app.presentation.screens.navigation.Screen
import org.braincards.app.presentation.screens.study.StudyScreen
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.DecksList) }

        Crossfade(targetState = currentScreen) { screen ->
            when (screen) {
                is Screen.DecksList -> {
                    DecksScreen(
                        onDeckClick = { deckId ->
                            currentScreen = Screen.FlashcardsList(deckId)
                        }
                    )
                }
                is Screen.FlashcardsList -> {
                    FlashcardScreen(
                        deckId = screen.deckId,
                        onBackClick = { currentScreen = Screen.DecksList },
                        onStudyClick = { deckId ->
                            currentScreen = Screen.StudySession(deckId)
                        }
                    )
                }
                is Screen.StudySession -> {
                    StudyScreen(
                        deckId = screen.deckId,
                        onBackClick = {
                            currentScreen = Screen.FlashcardsList(screen.deckId)
                        }
                    )
                }
            }
        }
    }
}