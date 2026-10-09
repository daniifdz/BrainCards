package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.AuthState

interface AuthRepository {
    val authState: Flow<AuthState>
    suspend fun signUp(email: String, password: String)
    suspend fun signIn(email: String, password: String)
    suspend fun signOut()
    suspend fun sendPasswordResetEmail(email: String)

    // Bloque F (borrado de cuenta) -- solo el borrado remoto (RPC delete_own_account).
    // El cierre de sesion local es responsabilidad de signOut(), ya existente; no se
    // llama desde aqui para que el caller (DeleteAccountUseCase) pueda decidir que
    // hacer si signOut() falla despues de un borrado remoto ya consumado.
    suspend fun deleteAccount()
}
