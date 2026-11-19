package org.braincards.app.data.mapper

import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import org.braincards.app.domain.models.AuthState

/**
 * Mapeo puro SessionStatus -> AuthState. [savedSession] viene ya resuelto por el caller (AuthRepositoryImpl), que solo lee
 * sessionManager.loadSession() cuando el estado es RefreshFailure: aquí no se hace I/O.
 *
 * RefreshFailure (NetworkError o InternalServerError, según auth-kt 3.1.4) nunca borra la sesión guardada: clearSession()
 * solo se llama cuando el servidor rechaza de verdad el refresh token (eso llega aquí como NotAuthenticated). Por eso, si
 * hay sesión guardada, se mapea a Authenticated con sessionConfirmed = false en lugar de NotAuthenticated; si no, un
 * arranque sin conexión con una sesión válida parecería «nunca inició sesión». Sin sesión guardada, NotAuthenticated.
 */
fun SessionStatus.toAuthState(savedSession: UserSession?): AuthState = when (this) {
    is SessionStatus.Authenticated -> buildAuthenticatedState(session, sessionConfirmed = true)
    is SessionStatus.NotAuthenticated -> AuthState.NotAuthenticated
    is SessionStatus.Initializing -> AuthState.Loading
    is SessionStatus.RefreshFailure -> savedSession?.let { buildAuthenticatedState(it, sessionConfirmed = false) }
        ?: AuthState.NotAuthenticated
}

/**
 * [UserSession.user] es nullable -- en el flujo normal de email/password siempre
 * viene relleno, pero el tipo lo permite vacio. Si userId quedara en blanco, no se
 * construye un Authenticated("") : eso llegaria tal cual a SyncEngineUseCase, que
 * llamaria claimOrphanedDecks("")/claimOrphanedTopics("")/claimOrphanedFlashcards("")
 * -- adoptando las filas locales huerfanas con userId = "" en vez de dejarlas en
 * null, e inutilizandolas para siempre: claimOrphanedX solo busca userId IS NULL,
 * asi que ni una cuenta real futura ni "sin cuenta" podrian volver a reclamarlas.
 */
private fun buildAuthenticatedState(session: UserSession, sessionConfirmed: Boolean): AuthState {
    val userId = session.user?.id.orEmpty()
    if (userId.isBlank()) return AuthState.NotAuthenticated
    return AuthState.Authenticated(userId = userId, email = session.user?.email, sessionConfirmed = sessionConfirmed)
}
