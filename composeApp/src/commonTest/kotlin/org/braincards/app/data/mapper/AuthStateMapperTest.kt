package org.braincards.app.data.mapper

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import org.braincards.app.domain.models.AuthState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests del mapeo puro SessionStatus -> AuthState. Cubren que RefreshFailure (NetworkError o InternalServerError) con una
 * sesión guardada no colapsa a NotAuthenticated; ver AuthStateMapper.
 */
class AuthStateMapperTest {

    private fun buildSession(userId: String = "user-1", email: String? = "a@b.com") = UserSession(
        accessToken = "token",
        refreshToken = "refresh",
        expiresIn = 3600,
        tokenType = "bearer",
        user = UserInfo(aud = "authenticated", id = userId, email = email)
    )

    @Test
    fun `Authenticated se mapea a Authenticated confirmado`() {
        val status = SessionStatus.Authenticated(session = buildSession())

        val result = status.toAuthState(savedSession = null)

        assertEquals(
            AuthState.Authenticated(userId = "user-1", email = "a@b.com", sessionConfirmed = true),
            result
        )
    }

    @Test
    fun `NotAuthenticated se mapea a NotAuthenticated`() {
        val status = SessionStatus.NotAuthenticated(isSignOut = true)

        val result = status.toAuthState(savedSession = null)

        assertEquals(AuthState.NotAuthenticated, result)
    }

    @Test
    fun `Initializing se mapea a Loading`() {
        val result = SessionStatus.Initializing.toAuthState(savedSession = null)

        assertEquals(AuthState.Loading, result)
    }

    @Test
    fun `RefreshFailure por NetworkError con sesion guardada se mapea a Authenticated sin confirmar`() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException("sin red")))

        val result = status.toAuthState(savedSession = buildSession())

        assertEquals(
            AuthState.Authenticated(userId = "user-1", email = "a@b.com", sessionConfirmed = false),
            result
        )
    }

    @Test
    fun `RefreshFailure sin sesion guardada se mapea a NotAuthenticated`() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException("sin red")))

        val result = status.toAuthState(savedSession = null)

        assertEquals(AuthState.NotAuthenticated, result)
    }

    @Test
    fun `Authenticated con userId vacio se mapea a NotAuthenticated`() {
        val status = SessionStatus.Authenticated(session = buildSession(userId = ""))

        val result = status.toAuthState(savedSession = null)

        assertEquals(AuthState.NotAuthenticated, result)
    }

    @Test
    fun `RefreshFailure con sesion guardada pero userId vacio se mapea a NotAuthenticated`() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException("sin red")))

        val result = status.toAuthState(savedSession = buildSession(userId = ""))

        assertEquals(AuthState.NotAuthenticated, result)
    }
}
