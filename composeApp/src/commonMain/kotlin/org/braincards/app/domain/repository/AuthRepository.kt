package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.AuthState

interface AuthRepository {
    val authState: Flow<AuthState>
    suspend fun signUp(email: String, password: String)
    suspend fun signIn(email: String, password: String)
    suspend fun signOut()
    suspend fun sendPasswordResetEmail(email: String)
}
