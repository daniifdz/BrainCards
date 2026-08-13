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
import org.braincards.app.presentation.screens.studyStats.StudyStatsScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute


@Composable
@Preview
fun App() {
    var appTheme by remember { mutableStateOf(AppTheme.SYSTEM) }

    val useDarkTheme = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    MaterialTheme(colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()) {

        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = Screen.Home) {
            composable<Screen.Home> {
                HomeScreen(
                    onNavigateToDecks = { navController.navigate(Screen.DecksList) },
                    onNavigateToStudy = {
                        val destination = Screen.StudySession(deckId = null)
                        navController.navigate(destination)
                    },
                    onNavigateToSettings = { navController.navigate(Screen.Settings) },
                    onNavigateToStats = { navController.navigate(Screen.Stats) }
                )
            }
            composable<Screen.DecksList> {
                DecksScreen(
                    onDeckClick = { deckId ->
                        navController.navigate(Screen.FlashcardsList(deckId))
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )

            }
            composable<Screen.FlashcardsList> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.FlashcardsList>()
                FlashcardScreen(
                    deckId = route.deckId,
                    onBackClick = { navController.popBackStack() },
                    //ESTO ES LO MISMO QUE LO DE ABAJO PERO ACORTADO
                    //onStudyClick = {deckId ->
                    //  navController.navigate(Screen.StudySession(StudyScope.Deck(deckId)))
                    //},
                    onStudyClick = { deckId ->

                        val destination = Screen.StudySession(deckId = deckId)
                        navController.navigate(destination)
                    }
                )
            }
            composable<Screen.StudySession> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.StudySession>()
                StudyScreen(
                    deckId = route.deckId,

                    onBackClick = {
                        navController.popBackStack()
                    },
                    onGoToDecksClick = {
                        navController.navigate(Screen.DecksList)
                    }
                )
            }
            composable<Screen.Settings>{
                SettingsScreen(
                    currentTheme = appTheme,
                    onThemeChange = { newTheme -> appTheme = newTheme },
                    onBackClick = { navController.popBackStack() },
                )
            }
            composable<Screen.Stats>{
                StudyStatsScreen(
                    onBackClick = {navController.popBackStack()}
                )
            }

        }

    }
}

