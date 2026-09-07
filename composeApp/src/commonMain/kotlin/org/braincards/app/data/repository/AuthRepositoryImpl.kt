package org.braincards.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val client: SupabaseClient
) : AuthRepository {

    override val authState: Flow<AuthState> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> AuthState.Authenticated(
                userId = status.session.user?.id.orEmpty(),
                email = status.session.user?.email
            )
            is SessionStatus.NotAuthenticated -> AuthState.NotAuthenticated
            is SessionStatus.Initializing -> AuthState.Loading
            is SessionStatus.RefreshFailure -> AuthState.NotAuthenticated
        }
    }

    override suspend fun signUp(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        client.auth.resetPasswordForEmail(email)
    }
}
