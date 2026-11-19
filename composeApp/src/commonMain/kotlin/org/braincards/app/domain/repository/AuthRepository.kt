package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.SignUpOutcome

interface AuthRepository {
    val authState: Flow<AuthState>
    suspend fun signUp(email: String, password: String)
    suspend fun signIn(email: String, password: String)
    suspend fun signOut()
    suspend fun sendPasswordResetEmail(email: String)

    // Recuperacion de contraseña por codigo OTP (ver AuthStateMapper.kt --
    // verificar el codigo deja al usuario con una sesion Authenticated real antes
    // de que ponga la contraseña nueva, por eso updatePassword no necesita nada
    // mas que la sesion ya activa).
    suspend fun verifyPasswordResetCode(email: String, code: String)
    suspend fun updatePassword(newPassword: String)

    // Confirmacion de registro por codigo OTP. signUpWithConfirmation distingue si el
    // servidor devolvio sesion o si hay que verificar el codigo; verifySignUpCode deja
    // al usuario con una sesion Authenticated real (igual que verifyPasswordResetCode).
    suspend fun signUpWithConfirmation(email: String, password: String): SignUpOutcome
    suspend fun verifySignUpCode(email: String, code: String)
    suspend fun resendSignUpCode(email: String)

    // Borrado de cuenta: solo la parte remota (RPC delete_own_account). El cierre de sesión local es cosa de signOut(); no se
    // llama desde aquí para que el caller (DeleteAccountUseCase) decida qué hacer si signOut() falla tras un borrado remoto
    // ya consumado.
    suspend fun deleteAccount()

    // Cierra la sesion SOLO en local (borra la sesion guardada y pasa a NotAuthenticated), sin
    // red. Respaldo de signOut(), que hace una peticion de logout al servidor y, si no hay
    // conexion, lanza antes de limpiar nada (verificado en auth-kt 3.1.4, AuthImpl.signOut).
    suspend fun clearLocalSession()
}
