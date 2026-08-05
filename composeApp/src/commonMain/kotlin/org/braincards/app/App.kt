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
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.presentation.screens.home.HomeScreen
import org.braincards.app.presentation.screens.settings.AppTheme
import org.braincards.app.presentation.screens.settings.SettingsScreen
import org.braincards.app.presentation.screens.studyStats.StudyStatsScreen


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
                    HomeScreen(
                        onNavigateToDecks = { currentScreen = Screen.DecksList },
                        onNavigateToStudy = { currentScreen = Screen.StudySession(StudyScope.All) },
                        onNavigateToSettings = { currentScreen = Screen.Settings },
                        onNavigateToStats = { currentScreen = Screen.Stats }
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
                            currentScreen = Screen.StudySession(StudyScope.Deck(deckId))
                        }
                    )
                }

                is Screen.StudySession -> {
                    StudyScreen(
                        deckId = when (val scope = screen.scope) {
                            is StudyScope.All -> null
                            is StudyScope.Deck -> scope.deckId
                        },
                        onBackClick = {
                            currentScreen = Screen.Home
                        },
                        onGoToDecksClick = {
                            currentScreen = Screen.DecksList
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

                is Screen.Stats -> {
                    StudyStatsScreen(
                        onBackClick = { currentScreen = Screen.Home }
                    )
                }
            }
        }
    }
}

