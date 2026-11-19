package org.braincards.app.domain.fakes

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.SignUpOutcome
import org.braincards.app.domain.repository.AuthRepository
import kotlin.time.Duration

/**
 * Fake en memoria de AuthRepository -- el estado se controla desde el test
 * llamando a setState(...), que es lo unico que SyncEngineUseCase necesita
 * leer (authState.first()). Los metodos de escritura no los usa ningun test
 * de sync todavia, asi que fallan alto si alguna vez se llaman por error.
 */
class FakeAuthRepository(
    initialState: AuthState = AuthState.NotAuthenticated,
    // Registro compartido con otros fakes para comprobar el orden entre llamadas
    // (SignOutAndWipeUseCaseTest). null = no se registra nada.
    private val callLog: MutableList<String>? = null
) : AuthRepository {

    private val state = MutableStateFlow(initialState)
    override val authState: Flow<AuthState> = state

    // Controlables desde el test: si no son null, el método correspondiente lanza esa excepción en vez de «tener éxito».
    var deleteAccountFailure: Throwable? = null
    var signOutFailure: Throwable? = null
    // Si no es null, signOut() espera este tiempo (delay, respeta el tiempo virtual de runTest)
    // antes de cerrar la sesion: simula un logout remoto lento o colgado.
    var signOutDelay: Duration? = null
    // Compuerta: si no es null, signOut() espera a que el test la abra (sin tiempo, ni real ni
    // virtual). signOutEntered avisa de que signOut() ya esta parado ahi (SignOutFlowRoomTest).
    var signOutGate: CompletableDeferred<Unit>? = null
    val signOutEntered = CompletableDeferred<Unit>()
    var clearLocalSessionFailure: Throwable? = null
    var clearLocalSessionCalled = false
    var verifyPasswordResetCodeFailure: Throwable? = null
    var updatePasswordFailure: Throwable? = null

    var deleteAccountCalled = false
    var deleteAccountCallCount = 0
    var signOutCalled = false
    // Contadores, no solo booleanos -- ConfirmPasswordResetUseCaseTest necesita
    // comprobar que verify() NO se repite en un reintento.
    var verifyPasswordResetCodeCallCount = 0
    var updatePasswordCallCount = 0
    var lastVerifiedCode: String? = null

    // Confirmacion de registro por OTP.
    var signUpOutcome: SignUpOutcome = SignUpOutcome.ConfirmationRequired
    var signUpFailure: Throwable? = null
    var verifySignUpCodeFailure: Throwable? = null
    var resendSignUpCodeFailure: Throwable? = null

    var signUpCallCount = 0
    var lastSignUpEmail: String? = null
    var verifySignUpCodeCallCount = 0
    var lastVerifiedSignUpCode: String? = null
    var resendSignUpCodeCallCount = 0
    var lastResentEmail: String? = null

    fun setState(newState: AuthState) {
        state.value = newState
    }

    override suspend fun signUp(email: String, password: String) {
        throw UnsupportedOperationException("No usado en estos tests")
    }

    // Login (formularios de auth): falla con [signInFailure] o, si no, deja la sesion iniciada. [signInGate] lo
    // detiene hasta que el test lo abra (para probar el doble tap).
    var signInFailure: Throwable? = null
    var signInGate: CompletableDeferred<Unit>? = null
    var signInCallCount = 0

    override suspend fun signIn(email: String, password: String) {
        signInCallCount++
        signInGate?.await()
        signInFailure?.let { throw it }
        state.value = AuthState.Authenticated(userId = "user-1", email = email, sessionConfirmed = true)
    }

    // Antes lanzaba siempre (nadie lo necesitaba) -- DeleteAccountUseCaseTest si lo
    // necesita funcionando, como ultimo paso tras un borrado remoto exitoso.
    override suspend fun signOut() {
        callLog?.add("signOut")
        signOutCalled = true
        signOutEntered.complete(Unit)
        signOutGate?.await()
        signOutDelay?.let { delay(it) }
        signOutFailure?.let { throw it }
        state.value = AuthState.NotAuthenticated
    }

    override suspend fun clearLocalSession() {
        callLog?.add("clearLocalSession")
        clearLocalSessionCalled = true
        clearLocalSessionFailure?.let { throw it }
        state.value = AuthState.NotAuthenticated
    }

    // Recuperacion de contraseña: falla con [sendPasswordResetFailure] o, si no, "envia" el correo.
    var sendPasswordResetFailure: Throwable? = null
    var sendPasswordResetCallCount = 0

    override suspend fun sendPasswordResetEmail(email: String) {
        sendPasswordResetCallCount++
        sendPasswordResetFailure?.let { throw it }
    }

    override suspend fun deleteAccount() {
        deleteAccountCalled = true
        deleteAccountCallCount++
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

    override suspend fun signUpWithConfirmation(email: String, password: String): SignUpOutcome {
        signUpCallCount++
        lastSignUpEmail = email
        signUpFailure?.let { throw it }
        return signUpOutcome
    }

    override suspend fun verifySignUpCode(email: String, code: String) {
        verifySignUpCodeCallCount++
        lastVerifiedSignUpCode = code
        verifySignUpCodeFailure?.let { throw it }
    }

    override suspend fun resendSignUpCode(email: String) {
        resendSignUpCodeCallCount++
        lastResentEmail = email
        resendSignUpCodeFailure?.let { throw it }
    }
}
