package org.braincards.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.braincards.app.data.mapper.toAuthState
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val client: SupabaseClient
) : AuthRepository {

    // sessionManager.loadSession() solo se llama en RefreshFailure (lee de
    // almacenamiento local, sin red) -- ver AuthStateMapper.toAuthState para el
    // porque de no colapsar RefreshFailure a NotAuthenticated sin mirar esto.
    override val authState: Flow<AuthState> = client.auth.sessionStatus.map { status ->
        val savedSession = if (status is SessionStatus.RefreshFailure) {
            client.auth.sessionManager.loadSession()
        } else {
            null
        }
        status.toAuthState(savedSession)
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

    // Verificar el codigo deja al usuario con una sesion Authenticated real
    // (access + refresh token), igual que un signIn normal -- confirmado en el
    // codigo fuente real de auth-kt (AuthImpl.verify() llama importSession()).
    // updatePassword() se apoya en esa sesion ya activa, sin nada mas.
    override suspend fun verifyPasswordResetCode(email: String, code: String) {
        client.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code)
    }

    override suspend fun updatePassword(newPassword: String) {
        client.auth.updateUser { password = newPassword }
    }

    // Bloque F -- solo el borrado remoto (RPC security definer, borra de auth.users;
    // el ON DELETE CASCADE del esquema se encarga de decks/topics/flashcards/reviewlog
    // en Supabase). El cierre de sesion local es responsabilidad de signOut(), no de
    // este metodo -- ver comentario en AuthRepository.deleteAccount().
    override suspend fun deleteAccount() {
        client.postgrest.rpc("delete_own_account")
    }
}
