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

    // Controlables desde el test (Bloque F, DeleteAccountUseCaseTest): si no son null,
    // el metodo correspondiente lanza esa excepcion en vez de "tener exito".
    var deleteAccountFailure: Throwable? = null
    var signOutFailure: Throwable? = null
    var verifyPasswordResetCodeFailure: Throwable? = null
    var updatePasswordFailure: Throwable? = null

    var deleteAccountCalled = false
    var signOutCalled = false
    // Contadores, no solo booleanos -- ConfirmPasswordResetUseCaseTest necesita
    // comprobar que verify() NO se repite en un reintento.
    var verifyPasswordResetCodeCallCount = 0
    var updatePasswordCallCount = 0
    var lastVerifiedCode: String? = null

    fun setState(newState: AuthState) {
        state.value = newState
    }

    override suspend fun signUp(email: String, password: String) {
        throw UnsupportedOperationException("No usado en estos tests")
    }

    override suspend fun signIn(email: String, password: String) {
        throw UnsupportedOperationException("No usado en estos tests")
    }

    // Antes lanzaba siempre (nadie lo necesitaba) -- DeleteAccountUseCaseTest si lo
    // necesita funcionando, como ultimo paso tras un borrado remoto exitoso.
    override suspend fun signOut() {
        signOutCalled = true
        signOutFailure?.let { throw it }
        state.value = AuthState.NotAuthenticated
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        throw UnsupportedOperationException("No usado en estos tests")
    }

    override suspend fun deleteAccount() {
        deleteAccountCalled = true
        deleteAccountFailure?.let { throw it }
    }

    override suspend fun verifyPasswordResetCode(email: String, code: String) {
        verifyPasswordResetCodeCallCount++
        lastVerifiedCode = code
        verifyPasswordResetCodeFailure?.let { throw it }
    }

    override suspend fun updatePassword(newPassword: String) {
        updatePasswordCallCount++
        updatePasswordFailure?.let { throw it }
    }
}
