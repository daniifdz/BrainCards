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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.braincards.app.presentation.screens.home.HomeScreen
import org.braincards.app.presentation.screens.settings.AppTheme
import org.braincards.app.presentation.screens.settings.SettingsScreen


@Composable
@Preview
fun App() {
    var appTheme by remember { mutableStateOf(AppTheme.SYSTEM) }

    val useDarkTheme = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()
    ) {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

        Crossfade(targetState = currentScreen) { screen ->
            when (screen) {
                is Screen.Home -> {
                    // Necesitas importar HomeScreen
                    HomeScreen(
                        onNavigateToDecks = { currentScreen = Screen.DecksList },
                        onNavigateToStudy = { /* TODO: Implementar modo estudio global luego */ },
                        onNavigateToSettings = { currentScreen = Screen.Settings }
                    )
                }
                is Screen.DecksList -> {
                    DecksScreen(
                        onDeckClick = { deckId ->
                            currentScreen = Screen.FlashcardsList(deckId)
                        },
                        onBackClick = {
                            currentScreen = Screen.Home
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
                is Screen.Settings -> {
                    SettingsScreen(
                        currentTheme = appTheme,
                        onThemeChange = { newTheme -> appTheme = newTheme },
                        onBackClick = { currentScreen = Screen.Home }
                    )
                }
            }
        }
    }
}

