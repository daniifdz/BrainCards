package org.braincards.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import org.braincards.app.platform.EntryPendingSystemBars
import org.braincards.app.platform.rememberReducedMotion
import org.braincards.app.presentation.screens.navigation.SPLASH_FADE_MS
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import org.braincards.app.presentation.screens.auth.AuthScreen
import org.braincards.app.presentation.screens.auth.ConfirmEmailScreen
import org.braincards.app.presentation.screens.auth.ForgotPasswordScreen
import org.braincards.app.presentation.screens.auth.ResetPasswordScreen
import org.braincards.app.presentation.screens.decks.DecksScreen
import org.braincards.app.presentation.screens.flashcards.DeckContentScreen
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
import org.braincards.app.presentation.screens.navigation.WelcomeNotice
import org.braincards.app.presentation.screens.navigation.exitAuthFlow
import org.braincards.app.presentation.screens.navigation.goHomeClearingStack
import org.braincards.app.presentation.screens.navigation.goToWelcomeEntry
import org.braincards.app.presentation.screens.navigation.isAuthFlow
import org.braincards.app.presentation.screens.navigation.needsWelcomeGuard
import org.braincards.app.presentation.screens.navigation.popBackStackSafely
import org.braincards.app.presentation.theme.BrainCardsTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.braincards.app.presentation.screens.navigation.THEME_LOAD_TIMEOUT_MS
import org.braincards.app.presentation.screens.navigation.SplashExitSignal
import org.braincards.app.presentation.screens.navigation.rememberSplashTimeline
import org.braincards.app.presentation.screens.navigation.shouldShowEntryPending
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
fun App(coldStartSplash: SplashExitSignal? = null) {
    val themeViewModel = koinViewModel<ThemeViewModel>()
    val appTheme by themeViewModel.theme.collectAsState()

    val useDarkTheme = resolveDarkTheme(appTheme, isSystemInDarkTheme())

    val entryViewModel = koinViewModel<AppEntryViewModel>()
    val startRoute by entryViewModel.startRoute.collectAsState()
    val welcomeNotice by entryViewModel.welcomeNotice.collectAsState()

    // Espera al tema guardado (max THEME_LOAD_TIMEOUT_MS) para no arrancar con el tema del sistema y saltar despues.
    var themeTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(THEME_LOAD_TIMEOUT_MS)
        themeTimedOut = true
    }
    // La transicion de color solo se activa un fotograma despues de leer el tema: la carga inicial es un salto, no un fundido.
    val animateTheme by produceState(false, appTheme != null) {
        if (appTheme != null) {
            withFrameNanos { }
            value = true
        }
    }

    BrainCardsTheme(darkTheme = useDarkTheme, animate = animateTheme) {
        // Auth sin resolver (arranque en frio) o tema sin leer: EntryPending (fondo morado fijo + cerebro), nunca Bienvenida ni Home.
        // La navegacion solo se COMPONE cuando ya no esta pendiente (mismo punto de composicion de siempre: el NavHost no se
        // recrea ni pierde estado al rotar) y EntryPending queda ENCIMA y se desvanece (~250 ms; instantaneo con "reducir
        // animaciones"). Tras rotar con la ruta ya resuelta, pending = false desde el primer frame: no hay capa ni fundido.
        val pending = shouldShowEntryPending(startRoute, themeLoaded = appTheme != null, themeTimedOut = themeTimedOut)
        val reducedMotion = rememberReducedMotion()
        // Reloj de la coreografia y permanencia de la capa (contrato D8): con la navegacion lista antes de 450 ms la capa se
        // queda hasta 450 ms; la navegacion se compone igualmente por debajo (no se retrasa, solo se tapa).
        val splash = rememberSplashTimeline(pending, reducedMotion, coldStartSplash)
        // Iconos claros de la barra de estado mientras EntryPending se ve; se bajan al EMPEZAR el fundido (overlayVisible = false).
        EntryPendingSystemBars(visible = splash.overlayVisible)
        Box(Modifier.fillMaxSize()) {
            if (!pending) {
                AppNavigation(
                    entryViewModel = entryViewModel,
                    themeViewModel = themeViewModel,
                    startRoute = startRoute,
                    appTheme = appTheme,
                    welcomeNotice = welcomeNotice
                )
            }
            AnimatedVisibility(
                visible = splash.overlayVisible,
                enter = EnterTransition.None,
                exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(SPLASH_FADE_MS))
            ) {
                EntryPendingScreen(elapsedMs = splash::elapsedMs, onFirstDraw = splash::markFirstDraw)
            }
        }
    }
}

@OptIn(KoinExperimentalAPI::class)
@Composable
private fun AppNavigation(
    entryViewModel: AppEntryViewModel,
    themeViewModel: ThemeViewModel,
    startRoute: EntryRoute,
    appTheme: AppTheme?,
    welcomeNotice: WelcomeNotice?
) {
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
                    navController.popBackStackSafely()
                }
            )

        }
        composable<Screen.FlashcardsList> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.FlashcardsList>()
            DeckContentScreen(
                deckId = route.deckId,
                onBackClick = { navController.popBackStackSafely() },
                // Si el mazo desaparece (p. ej. borrado en otro dispositivo): salida segura, nunca popBackStack().
                onDeckGone = { navController.popBackStackSafely() },
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
                    navController.popBackStackSafely()
                }
            )
        }
        composable<Screen.Settings>{
            SettingsScreen(
                currentTheme = appTheme?: AppTheme.SYSTEM,
                onThemeChange = { newTheme -> themeViewModel.setTheme(newTheme) },
                onBackClick = { navController.popBackStackSafely() },
                onNavigateToAuth = { navController.navigate(Screen.Auth()) },
            )
        }
        composable<Screen.Stats>{
            StudyStatsScreen(
                onBackClick = {navController.popBackStackSafely()},
                onCreateDeck = { navController.navigate(Screen.DecksList) }
            )
        }
        composable<Screen.Auth> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Auth>()
            AuthScreen(
                asEntry = route.asEntry,
                onBackClick = { navController.popBackStackSafely() },
                // Entrada: se elige el modo local y se va a Home reemplazando la pantalla. Apilada: se vuelve al origen.
                onContinueWithoutAccount = {
                    if (route.asEntry) {
                        scope.launch {
                            entryViewModel.chooseLocalMode()
                            navController.goHomeClearingStack()
                        }
                    } else if (!navController.popBackStackSafely()) {
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
                },
                // Aviso de salida de sesion (Snackbar de un solo uso / nota persistente), tambien en la variante con flecha.
                // Vive en AppEntryViewModel (Activity-scoped), no en el koinViewModel de esta entrada de navegacion.
                notice = welcomeNotice,
                onNoticeConsumed = entryViewModel::consumeWelcomeNotice,
                onNoticeDismissed = entryViewModel::dismissWelcomeNotice
            )
        }
        composable<Screen.ConfirmEmail> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.ConfirmEmail>()
            ConfirmEmailScreen(
                email = route.email,
                codeJustSent = route.codeJustSent,
                onConfirmed = { navController.exitAuthFlow() },
                onBackToLogin = { navController.popBackStackSafely() }
            )
        }
        composable<Screen.ForgotPassword> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.ForgotPassword>()
            ForgotPasswordScreen(
                initialEmail = route.email,
                onBackClick = { navController.popBackStackSafely() },
                onCodeSent = { email -> navController.navigate(Screen.ResetPassword(email)) }
            )
        }
        composable<Screen.ResetPassword> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.ResetPassword>()
            ResetPasswordScreen(
                email = route.email,
                onBackClick = { navController.popBackStackSafely() },
                // "Volver a iniciar sesion": hasta Bienvenida (se descartan Recuperar y Restablecer).
                onBackToLogin = { navController.popBackStack<Screen.Auth>(inclusive = false) },
                onPasswordResetCompleted = { navController.exitAuthFlow() }
            )
        }

    }
}
