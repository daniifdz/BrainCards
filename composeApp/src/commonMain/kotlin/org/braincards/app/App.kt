package org.braincards.app

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import org.braincards.app.presentation.screens.auth.AuthScreen
import org.braincards.app.presentation.screens.auth.ConfirmEmailScreen
import org.braincards.app.presentation.screens.auth.ForgotPasswordScreen
import org.braincards.app.presentation.screens.auth.ResetPasswordScreen
import org.braincards.app.presentation.screens.decks.DecksScreen
import org.braincards.app.presentation.screens.flashcards.FlashcardScreen
import org.braincards.app.presentation.screens.navigation.Screen
import org.braincards.app.presentation.screens.study.StudyScreen
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.isSystemInDarkTheme
import org.braincards.app.presentation.screens.home.HomeScreen
import org.braincards.app.presentation.screens.settings.AppTheme
import org.braincards.app.presentation.screens.settings.SettingsScreen
import org.braincards.app.presentation.screens.settings.ThemeViewModel
import org.braincards.app.presentation.screens.navigation.AppEntryViewModel
import org.braincards.app.presentation.screens.navigation.EntryPendingScreen
import org.braincards.app.presentation.screens.navigation.EntryRoute
import org.braincards.app.presentation.screens.navigation.exitAuthFlow
import org.braincards.app.presentation.screens.navigation.goHomeClearingStack
import org.braincards.app.presentation.screens.navigation.goToWelcomeEntry
import org.braincards.app.presentation.screens.navigation.isAuthFlow
import org.braincards.app.presentation.screens.navigation.needsWelcomeGuard
import org.braincards.app.presentation.theme.BrainCardsTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.braincards.app.presentation.theme.resolveDarkTheme
import org.braincards.app.presentation.screens.studyStats.StudyStatsScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI


@OptIn(KoinExperimentalAPI::class)
@Composable
@Preview
fun App() {
    val themeViewModel = koinViewModel<ThemeViewModel>()
    val appTheme by themeViewModel.theme.collectAsState()

    val useDarkTheme = resolveDarkTheme(appTheme, isSystemInDarkTheme())

    val entryViewModel = koinViewModel<AppEntryViewModel>()
    val startRoute by entryViewModel.startRoute.collectAsState()

    BrainCardsTheme(darkTheme = useDarkTheme) {
        // Auth sin resolver (arranque en frio): solo logo, nunca Bienvenida ni Home.
        if (startRoute == EntryRoute.Pending) {
            EntryPendingScreen()
            return@BrainCardsTheme
        }

        val navController = rememberNavController()
        val scope = rememberCoroutineScope()

        // Sesion perdida (cierre de sesion, borrado de cuenta, token revocado): Bienvenida de entrada con la pila vacia.
        // Channel de un solo consumo: no se repite al recomponer ni al rotar.
        LaunchedEffect(navController) {
            entryViewModel.signedOut.collect { navController.goToWelcomeEntry() }
        }
        // Pila restaurada por el sistema (Android) que no encaja con la ruta de arranque: a Bienvenida.
        LaunchedEffect(navController, startRoute) {
            val current = navController.currentBackStackEntryFlow.first().destination
            if (needsWelcomeGuard(startRoute, current.isAuthFlow())) navController.goToWelcomeEntry()
        }

        NavHost(
            navController = navController,
            startDestination = if (startRoute == EntryRoute.Home) Screen.Home else Screen.Auth(asEntry = true)
        ) {
            composable<Screen.Home> {
                HomeScreen(
                    onNavigateToDecks = { navController.navigate(Screen.DecksList) },
                    onNavigateToStudy = {
                        val destination = Screen.StudySession(deckId = null)
                        navController.navigate(destination)
                    },
                    onNavigateToSettings = { navController.navigate(Screen.Settings) },
                    onNavigateToStats = { navController.navigate(Screen.Stats) },
                    // Invitado: la pildora lleva a Bienvenida apilada (con flecha de vuelta).
                    onNavigateToAuth = { navController.navigate(Screen.Auth()) }
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
                    }
                )
            }
            composable<Screen.Settings>{
                SettingsScreen(
                    currentTheme = appTheme?: AppTheme.SYSTEM,
                    onThemeChange = { newTheme -> themeViewModel.setTheme(newTheme) },
                    onBackClick = { navController.popBackStack() },
                    onNavigateToAuth = { navController.navigate(Screen.Auth()) },
                )
            }
            composable<Screen.Stats>{
                StudyStatsScreen(
                    onBackClick = {navController.popBackStack()}
                )
            }
            composable<Screen.Auth> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.Auth>()
                AuthScreen(
                    asEntry = route.asEntry,
                    onBackClick = { navController.popBackStack() },
                    // Entrada: se elige el modo local y se va a Home reemplazando la pantalla. Apilada: se vuelve al origen.
                    onContinueWithoutAccount = {
                        if (route.asEntry) {
                            scope.launch {
                                entryViewModel.chooseLocalMode()
                                navController.goHomeClearingStack()
                            }
                        } else if (!navController.popBackStack()) {
                            navController.goHomeClearingStack()
                        }
                    },
                    onAuthenticated = { navController.exitAuthFlow() },
                    onForgotPasswordClick = { email -> navController.navigate(Screen.ForgotPassword(email)) },
                    onSignUpConfirmationRequired = { email ->
                        navController.navigate(Screen.ConfirmEmail(email, codeJustSent = true))
                    },
                    onEmailNotConfirmed = { email ->
                        navController.navigate(Screen.ConfirmEmail(email, codeJustSent = false))
                    }
                )
            }
            composable<Screen.ConfirmEmail> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.ConfirmEmail>()
                ConfirmEmailScreen(
                    email = route.email,
                    codeJustSent = route.codeJustSent,
                    onConfirmed = { navController.exitAuthFlow() },
                    onBackToLogin = { navController.popBackStack() }
                )
            }
            composable<Screen.ForgotPassword> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.ForgotPassword>()
                ForgotPasswordScreen(
                    initialEmail = route.email,
                    onBackClick = { navController.popBackStack() },
                    onCodeSent = { email -> navController.navigate(Screen.ResetPassword(email)) }
                )
            }
            composable<Screen.ResetPassword> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.ResetPassword>()
                ResetPasswordScreen(
                    email = route.email,
                    onBackClick = { navController.popBackStack() },
                    // "Volver a iniciar sesion": hasta Bienvenida (se descartan Recuperar y Restablecer).
                    onBackToLogin = { navController.popBackStack<Screen.Auth>(inclusive = false) },
                    onPasswordResetCompleted = { navController.exitAuthFlow() }
                )
            }

        }

    }
}

