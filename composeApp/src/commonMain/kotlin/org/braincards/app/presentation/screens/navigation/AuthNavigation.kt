package org.braincards.app.presentation.screens.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute

/**
 * Navegacion de entrada y de salida de los flujos de auth. La decision es pura (StartDestination.kt); aqui solo se
 * aplica sobre el NavController (se prueba a mano: no hay tests de composables).
 */

/**
 * "Atras" seguro para las flechas de las pantallas: solo hace pop si hay una pantalla a la que volver; con una sola
 * entrada en la pila (Home) no hace nada y devuelve false.
 *
 * Por que: `NavController.popBackStack()` NO protege el destino raiz; con `[Home]` saca Home y deja la pila VACIA (el
 * NavHost solo dibuja el fondo y nada responde). Y la transicion por defecto del NavHost dura 700 ms: tras el primer pop,
 * la pantalla que se va sigue compuesta y encima, con su flecha aun debajo del dedo. Un segundo toque en ese intervalo
 * volvia a llamar a `popBackStack()` con `[Home]`. Tras el primer pop `previousBackStackEntry` es null, asi que el
 * segundo toque se ignora.
 *
 * No sirve para `popBackStack<Screen.Auth>(...)`: ese apunta a una ruta concreta y no puede vaciar la pila.
 */
internal fun NavController.popBackStackSafely(): Boolean = previousBackStackEntry != null && popBackStack()

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
