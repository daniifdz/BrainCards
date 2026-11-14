package org.braincards.app.presentation.screens.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute

/**
 * Navegacion de entrada y de salida de los flujos de auth. La decision es pura (StartDestination.kt); aqui solo se
 * aplica sobre el NavController (se prueba a mano: no hay tests de composables).
 */

/** Vacia la pila y deja Home como unica pantalla. */
internal fun NavController.goHomeClearingStack() {
    navigate(Screen.Home) { popUpTo(graph.id) { inclusive = true } }
}

/** Vacia la pila y deja la Bienvenida como ENTRADA (sin flecha; «Continuar sin cuenta» lleva a Home). */
internal fun NavController.goToWelcomeEntry() {
    navigate(Screen.Auth(asEntry = true)) { popUpTo(graph.id) { inclusive = true } }
}

/**
 * `asEntry` de la Bienvenida que hay en la pila, o null si ya no esta (p. ej. tras restaurar el proceso): nunca lanza.
 */
internal fun NavController.authIsEntry(): Boolean? =
    runCatching { getBackStackEntry<Screen.Auth>().toRoute<Screen.Auth>().asEntry }.getOrNull()

/** Sale de un flujo de auth terminado bien: Home si era la entrada (o no se sabe), si no, vuelve al origen. */
internal fun NavController.exitAuthFlow() {
    when (authExitAction(authIsEntry())) {
        AuthExit.GoHome -> goHomeClearingStack()
        AuthExit.PopToOrigin -> if (!popBackStack<Screen.Auth>(inclusive = true)) goHomeClearingStack()
    }
}

/** ¿La pantalla actual es una de las de auth (Bienvenida, Confirmar, Recuperar, Restablecer)? */
internal fun NavDestination?.isAuthFlow(): Boolean =
    this != null && (hasRoute<Screen.Auth>() || hasRoute<Screen.ConfirmEmail>() ||
        hasRoute<Screen.ForgotPassword>() || hasRoute<Screen.ResetPassword>())
