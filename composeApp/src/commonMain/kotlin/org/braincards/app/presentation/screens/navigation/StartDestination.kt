package org.braincards.app.presentation.screens.navigation

import org.braincards.app.domain.models.AuthState

/** Donde arranca la app. [Pending] = aun no se sabe: solo splash (nunca Bienvenida ni Home). */
enum class EntryRoute { Pending, Home, Welcome }

/** Espera maxima del splash del sistema (Android) a que se resuelva la ruta; despues, pantalla de logo. */
const val ENTRY_SPLASH_MAX_WAIT_MS = 2_500L

/**
 * Ruta de arranque. [auth] es el estado ESTABLE (null = ninguno aun; Loading cuenta como ninguno).
 *  - sin estado estable -> Pending
 *  - Authenticated (confirmada o no) -> Home
 *  - NotAuthenticated: flag sin leer (null) -> Pending; elegido -> Home; no elegido -> Welcome
 */
fun resolveStartDestination(auth: AuthState?, localModeChosen: Boolean?): EntryRoute = when (auth) {
    null, AuthState.Loading -> EntryRoute.Pending
    is AuthState.Authenticated -> EntryRoute.Home
    AuthState.NotAuthenticated -> when (localModeChosen) {
        null -> EntryRoute.Pending
        true -> EntryRoute.Home
        false -> EntryRoute.Welcome
    }
}

/** Como se sale de un flujo de auth que ha terminado bien (sesion iniciada, codigo confirmado, contraseña nueva). */
enum class AuthExit {
    /** Bienvenida de entrada (o no se sabe): a Home, vaciando la pila. */
    GoHome,

    /** Bienvenida apilada: se vuelve a donde se estaba. */
    PopToOrigin
}

/** [asEntry] = null si la entrada de Auth ya no esta en la pila (p. ej. tras restaurar el proceso): salida segura a Home. */
fun authExitAction(asEntry: Boolean?): AuthExit =
    if (asEntry == false) AuthExit.PopToOrigin else AuthExit.GoHome

/**
 * Guardia de pila restaurada (Android): si la ruta de arranque es Welcome pero la pila que restauro el sistema no esta
 * en un flujo de auth (p. ej. mostraba Home), hay que llevar al usuario a Bienvenida.
 */
fun needsWelcomeGuard(initial: EntryRoute, currentIsAuthFlow: Boolean): Boolean =
    initial == EntryRoute.Welcome && !currentIsAuthFlow
