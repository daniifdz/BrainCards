package org.braincards.app.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.AuthRepository

/**
 * Fake en memoria de AuthRepository -- el estado se controla desde el test
 * llamando a setState(...), que es lo unico que SyncEngineUseCase necesita
 * leer (authState.first()). Los metodos de escritura no los usa ningun test
 * de sync todavia, asi que fallan alto si alguna vez se llaman por error.
 */
class FakeAuthRepository(
    initialState: AuthState = AuthState.NotAuthenticated
) : AuthRepository {

    private val state = MutableStateFlow(initialState)
    override val authState: Flow<AuthState> = state

    fun setState(newState: AuthState) {
        state.value = newState
    }

    override suspend fun signUp(email: String, password: String) {
        throw UnsupportedOperationException("No usado en estos tests")
    }

    override suspend fun signIn(email: String, password: String) {
        throw UnsupportedOperationException("No usado en estos tests")
    }

    override suspend fun signOut() {
        throw UnsupportedOperationException("No usado en estos tests")
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        throw UnsupportedOperationException("No usado en estos tests")
    }
}
