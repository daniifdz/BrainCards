package org.braincards.app.data.mapper

import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import org.braincards.app.domain.models.AuthState

/**
 * Mapeo puro SessionStatus -> AuthState. [savedSession] viene ya resuelto por el
 * caller (AuthRepositoryImpl), que solo lee sessionManager.loadSession() cuando
 * status es RefreshFailure -- aqui no se hace ningun I/O.
 *
 * RefreshFailure (NetworkError o InternalServerError, verificado en el codigo
 * fuente real de auth-kt 3.1.4, AuthImpl.kt) nunca borra la sesion guardada --
 * clearSession() solo se llama cuando el refresh token es rechazado de verdad por
 * el servidor (eso ya llega aqui como NotAuthenticated, no como RefreshFailure).
 * Por eso, si hay una sesion guardada, se mapea a Authenticated con
 * sessionConfirmed = false en vez de NotAuthenticated -- de lo contrario, un
 * arranque sin conexion con una sesion valida parecería "nunca inicio sesion".
 * Sin sesion guardada, NotAuthenticated (no hay nada que mostrar como cuenta).
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
