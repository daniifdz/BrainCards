package org.braincards.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.braincards.app.data.mapper.isEmailNotConfirmed
import org.braincards.app.data.mapper.toAuthErrorMessage
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.SignUpOutcome
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.usecase.Auth.ConfirmPasswordResetUseCase
import org.braincards.app.domain.usecase.Auth.DeleteAccountUseCase
import org.braincards.app.domain.usecase.Auth.ResendSignUpCodeUseCase
import org.braincards.app.domain.usecase.Auth.SendPasswordResetUseCase
import org.braincards.app.domain.usecase.Auth.SignInUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeResult
import org.braincards.app.domain.usecase.Auth.SignUpUseCase
import org.braincards.app.domain.usecase.Auth.VerifySignUpCodeUseCase
import org.braincards.app.domain.usecase.Sync.PullFailure
import org.braincards.app.domain.usecase.Sync.SyncResult
import org.braincards.app.domain.usecase.Sync.abortedTableCount
import org.braincards.app.domain.usecase.Sync.failedRowCount
import org.braincards.app.presentation.sync.FinishSignOutResult
import org.braincards.app.presentation.sync.SignOutPrompt
import org.braincards.app.presentation.sync.SyncCoordinator

/**
 * Segundos de espera en el boton "Reenviar codigo" tras cada envio de un codigo de
 * confirmacion de registro. Es solo una ayuda de UI para no malgastar peticiones: el
 * servidor valida su propio limite de envio por email igualmente (un minimo de ~60 s
 * entre emails al mismo usuario, mas el limite por hora del proyecto), y si se
 * supera responde over_email_send_rate_limit, que AuthErrorMapper ya traduce.
 */
const val RESEND_CODE_COOLDOWN_SECONDS = 60

/**
 * Como acabo un cierre de sesion. Es un enum y no un String porque el ViewModel no puede
 * resolver recursos (stringResource): SettingsScreen traduce cada caso a su texto.
 */
enum class SignOutEvent { SignedOut, SessionCloseFailed, WipeFailed, SessionChanged }

/** Resultado del sync manual para el usuario. Cada caso tiene su texto en strings.xml. */
sealed interface SyncUserMessage {
    data object NotAuthenticated : SyncUserMessage
    data object Synced : SyncUserMessage

    /** Fallaron el pull y el push a la vez. */
    data object SyncFailed : SyncUserMessage

    /** Pull abortado por red/timeout/5xx (o por una causa sin clasificar). */
    data object PullNetwork : SyncUserMessage

    /** Pull abortado porque el servidor rechazo la peticion (4xx: sesion o permisos). */
    data object PullRejected : SyncUserMessage

    /** Pull abortado al guardar las filas descargadas en este dispositivo. */
    data object PullLocal : SyncUserMessage

    /** El pull fue bien pero [pendingRows] filas no se pudieron subir. */
    data class PushPartial(val pendingRows: Int) : SyncUserMessage
}

/**
 * Mensaje para el usuario de un sync manual. Si el pull falla y el push tambien, un unico texto
 * combinado. Si solo falla el pull, el texto depende de la causa; con varias tablas abortadas por
 * causas distintas manda la mas grave: LOCAL > REJECTED > NETWORK (sin clasificar cuenta como red).
 */
fun SyncResult.toSyncUserMessage(): SyncUserMessage = when (this) {
    is SyncResult.NotAuthenticated -> SyncUserMessage.NotAuthenticated
    is SyncResult.Completed -> {
        val pullAbortedCount = pull.abortedTableCount()
        val pushFailedCount = push.failedRowCount()
        when {
            pullAbortedCount > 0 && pushFailedCount > 0 -> SyncUserMessage.SyncFailed
            pullAbortedCount > 0 -> {
                val failures = listOf(pull.decks, pull.topics, pull.flashcards, pull.cardScheduling)
                    .filter { it.aborted }
                    .map { it.failure }
                when {
                    PullFailure.LOCAL in failures -> SyncUserMessage.PullLocal
                    PullFailure.REJECTED in failures -> SyncUserMessage.PullRejected
                    else -> SyncUserMessage.PullNetwork
                }
            }
            pushFailedCount > 0 -> SyncUserMessage.PushPartial(pushFailedCount)
            else -> SyncUserMessage.Synced
        }
    }
}

class AuthViewModel(
    authRepository: AuthRepository,
    private val signUpUseCase: SignUpUseCase,
    private val signInUseCase: SignInUseCase,
    private val sendPasswordResetUseCase: SendPasswordResetUseCase,
    private val confirmPasswordResetUseCase: ConfirmPasswordResetUseCase,
    private val verifySignUpCodeUseCase: VerifySignUpCodeUseCase,
    private val resendSignUpCodeUseCase: ResendSignUpCodeUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AuthState.Loading
        )

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()

    // Recuperacion de contraseña por OTP.
    private val _passwordResetCodeSentEvent = MutableSharedFlow<String>()
    val passwordResetCodeSentEvent: SharedFlow<String> = _passwordResetCodeSentEvent.asSharedFlow()

    private val _passwordResetCompletedEvent = MutableSharedFlow<Unit>()
    val passwordResetCompletedEvent: SharedFlow<Unit> = _passwordResetCompletedEvent.asSharedFlow()

    // true solo si un verify() tuvo exito pero el updatePassword() posterior
    // fallo -- ver ConfirmPasswordResetUseCase. Permite que ResetPasswordScreen
    // deshabilite el campo del codigo y no pida reescribirlo en el reintento.
    private val _codeVerified = MutableStateFlow(false)
    val codeVerified: StateFlow<Boolean> = _codeVerified.asStateFlow()

    // Confirmacion de registro por OTP. Cada pantalla tiene su propia instancia de
    // este ViewModel (NavBackStackEntry), asi que los eventos los recoge la pantalla
    // que los provoco y el cooldown se arranca desde la pantalla del codigo.
    private val _signUpConfirmationRequiredEvent = MutableSharedFlow<String>()
    val signUpConfirmationRequiredEvent: SharedFlow<String> = _signUpConfirmationRequiredEvent.asSharedFlow()

    private val _emailNotConfirmedEvent = MutableSharedFlow<String>()
    val emailNotConfirmedEvent: SharedFlow<String> = _emailNotConfirmedEvent.asSharedFlow()

    private val _signUpConfirmedEvent = MutableSharedFlow<Unit>()
    val signUpConfirmedEvent: SharedFlow<Unit> = _signUpConfirmedEvent.asSharedFlow()

    private val _resendCooldownSeconds = MutableStateFlow(0)
    val resendCooldownSeconds: StateFlow<Int> = _resendCooldownSeconds.asStateFlow()
    private var resendCooldownJob: Job? = null
    private var initialCooldownStarted = false

    // El sync automatico (login/arranque con sesion activa) y su Mutex viven ahora
    // en SyncCoordinator (singleton real, no per-pantalla como este ViewModel) --
    // ver cabecera de SyncCoordinator.kt. Aqui solo se exponen sus StateFlow.
    val isSyncing: StateFlow<Boolean> = syncCoordinator.isSyncing
    val lastSuccessfulSyncAt: StateFlow<Instant?> = syncCoordinator.lastSuccessfulSyncAt
    val pendingChangesCount: StateFlow<Int> = syncCoordinator.pendingChangesCount

    /** Disparador manual del boton "Sincronizar ahora" de Ajustes -- este si informa
     * del resultado, a diferencia del sync automatico de login/arranque. */
    fun syncNow() {
        viewModelScope.launch {
            val result = syncCoordinator.syncNow()
            _syncMessageEvent.emit(result.toSyncUserMessage())
        }
    }

    // Resultado del sync manual como TIPO, no como String: el ViewModel no resuelve recursos,
    // SettingsScreen traduce cada caso a su texto en strings.xml.
    private val _syncMessageEvent = MutableSharedFlow<SyncUserMessage>()
    val syncMessageEvent: SharedFlow<SyncUserMessage> = _syncMessageEvent.asSharedFlow()

    fun signUp(email: String, password: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                // SessionStarted: la sesion ya esta importada y authState navega solo.
                if (signUpUseCase(email, password) == SignUpOutcome.ConfirmationRequired) {
                    _signUpConfirmationRequiredEvent.emit(email)
                }
            } catch (e: IllegalArgumentException) {
                _messageEvent.emit(e.message ?: "Error al registrarse")
            } catch (e: Exception) {
                println("Error al registrarse: $e")
                _messageEvent.emit(e.toAuthErrorMessage())
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                signInUseCase(email, password)
            } catch (e: IllegalArgumentException) {
                _messageEvent.emit(e.message ?: "Error al iniciar sesión")
            } catch (e: Exception) {
                println("Error al iniciar sesión: $e")
                if (e.isEmailNotConfirmed()) {
                    _emailNotConfirmedEvent.emit(email)
                } else {
                    _messageEvent.emit(e.toAuthErrorMessage())
                }
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    // --- Cierre de sesion con limpieza de la BD local (aislamiento entre cuentas) ---
    // Todo el estado vive aqui, no en el Composable: sobrevive a la rotacion en Android.

    // No nulo = dialogo de confirmacion abierto con N cambios sin sincronizar.
    private val _signOutPrompt = MutableStateFlow<SignOutPrompt.NeedsConfirmation?>(null)
    val signOutPrompt: StateFlow<SignOutPrompt.NeedsConfirmation?> = _signOutPrompt.asStateFlow()

    // true mientras se prepara (sync previo) o se ejecuta el borrado; NO mientras el dialogo
    // espera la decision del usuario (si no, no podria pulsar sus botones).
    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    private val _signOutEvent = MutableSharedFlow<SignOutEvent>()
    val signOutEvent: SharedFlow<SignOutEvent> = _signOutEvent.asSharedFlow()

    // Cuenta que pidio cerrar sesion: finishSignOut valida que siga siendo la misma.
    private var signOutUserId: String? = null

    /** Boton "Cerrar sesion": sincroniza, cuenta lo que se perderia y, si hay algo, abre el
     * dialogo; si no hay nada que perder, cierra directamente. */
    fun requestSignOut() {
        if (_isSigningOut.value || _signOutPrompt.value != null) return
        val userId = (authState.value as? AuthState.Authenticated)?.userId ?: return
        _isSigningOut.value = true
        signOutUserId = userId
        viewModelScope.launch {
            try {
                when (val prompt = syncCoordinator.prepareSignOut()) {
                    SignOutPrompt.ReadyToSignOut -> finishSignOut(userId, confirmedCount = 0)
                    is SignOutPrompt.NeedsConfirmation -> _signOutPrompt.value = prompt
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Error al preparar el cierre de sesión: $e")
                _signOutEvent.emit(SignOutEvent.WipeFailed)
            } finally {
                _isSigningOut.value = false
            }
        }
    }

    /** "Cerrar sesion y borrar" del dialogo. */
    fun confirmSignOut() {
        if (_isSigningOut.value) return
        val prompt = _signOutPrompt.value ?: return
        val userId = signOutUserId ?: return
        _isSigningOut.value = true
        viewModelScope.launch {
            try {
                finishSignOut(userId, prompt.unsyncedChanges)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Error al cerrar sesión: $e")
                _signOutPrompt.value = null
                _signOutEvent.emit(SignOutEvent.WipeFailed)
            } finally {
                _isSigningOut.value = false
            }
        }
    }

    /** "Cancelar" del dialogo: no se borra nada. Se ignora a mitad del borrado. */
    fun dismissSignOut() {
        if (_isSigningOut.value) return
        _signOutPrompt.value = null
        signOutUserId = null
    }

    private suspend fun finishSignOut(userId: String, confirmedCount: Int) {
        when (val result = syncCoordinator.finishSignOut(userId, confirmedCount)) {
            // Entraron mas cambios con el dialogo abierto: se vuelve a pedir con el numero nuevo.
            is FinishSignOutResult.NeedsReconfirmation ->
                _signOutPrompt.value = SignOutPrompt.NeedsConfirmation(result.unsyncedChanges)
            is FinishSignOutResult.Finished -> {
                _signOutPrompt.value = null
                signOutUserId = null
                _signOutEvent.emit(result.outcome.toSignOutEvent())
            }
        }
    }

    private fun SignOutAndWipeResult.toSignOutEvent(): SignOutEvent = when (this) {
        SignOutAndWipeResult.SignedOut -> SignOutEvent.SignedOut
        SignOutAndWipeResult.SessionChanged -> SignOutEvent.SessionChanged
        is SignOutAndWipeResult.WipeFailed -> SignOutEvent.WipeFailed
        is SignOutAndWipeResult.SessionCloseFailed -> SignOutEvent.SessionCloseFailed
    }

    /** Borrado de cuenta (Bloque F) -- bloquea doble tap con el mismo criterio que
     * isSubmitting/isSyncing, y toma el Mutex del coordinador para no correr a la
     * vez que un sync. */
    fun deleteAccount() {
        if (_isDeletingAccount.value) return
        val userId = (authState.value as? AuthState.Authenticated)?.userId ?: return
        viewModelScope.launch {
            syncCoordinator.withSyncLock {
                _isDeletingAccount.value = true
                try {
                    deleteAccountUseCase(userId)
                    // La siguiente cuenta no debe heredar "ultima sincronizacion" ni el
                    // contador de pendientes de esta (los datos locales si se conservan).
                    syncCoordinator.resetSyncState()
                    _messageEvent.emit("Cuenta eliminada")
                } catch (e: Exception) {
                    println("Error al eliminar la cuenta: $e")
                    _messageEvent.emit("No se pudo eliminar la cuenta")
                } finally {
                    _isDeletingAccount.value = false
                }
            }
        }
    }

    /** Envio inicial (ForgotPasswordScreen) y reenvio (boton "reenviar codigo" en
     * ResetPasswordScreen) comparten este metodo -- un codigo nuevo invalida
     * cualquier verificacion previa (ConfirmPasswordResetUseCase.onNewCodeRequested). */
    fun sendPasswordReset(email: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                sendPasswordResetUseCase(email)
                confirmPasswordResetUseCase.onNewCodeRequested()
                _codeVerified.value = false
                _messageEvent.emit("Te hemos enviado un correo para restablecer tu contraseña")
                _passwordResetCodeSentEvent.emit(email)
            } catch (e: IllegalArgumentException) {
                _messageEvent.emit(e.message ?: "Error al enviar el correo")
            } catch (e: Exception) {
                println("Error al enviar el correo de recuperación: $e")
                _messageEvent.emit(e.toAuthErrorMessage())
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /** Verifica el codigo de confirmacion de registro. Al tener exito la sesion queda
     * importada (authState pasa a Authenticated y el SyncCoordinator sincroniza solo);
     * aqui solo se avisa a la pantalla para que cierre el flujo. */
    fun confirmSignUp(email: String, code: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                verifySignUpCodeUseCase(email, code)
                _messageEvent.emit("Cuenta confirmada")
                _signUpConfirmedEvent.emit(Unit)
            } catch (e: IllegalArgumentException) {
                _messageEvent.emit(e.message ?: "Error al confirmar la cuenta")
            } catch (e: Exception) {
                println("Error al confirmar el registro: $e")
                _messageEvent.emit(e.toAuthErrorMessage())
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /** Reenvia el codigo de confirmacion y reinicia el cooldown del boton. Si el
     * servidor lo rechaza (rate limit), el cooldown no se reinicia. */
    fun resendSignUpCode(email: String) {
        if (_isSubmitting.value || _resendCooldownSeconds.value > 0) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                resendSignUpCodeUseCase(email)
                _messageEvent.emit("Te hemos enviado un código nuevo")
                startResendCooldown()
            } catch (e: IllegalArgumentException) {
                _messageEvent.emit(e.message ?: "Error al reenviar el código")
            } catch (e: Exception) {
                println("Error al reenviar el codigo de registro: $e")
                _messageEvent.emit(e.toAuthErrorMessage())
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /** La pantalla del codigo lo llama al abrirse justo despues de registrarse (el
     * email de confirmacion acaba de enviarse). Idempotente: sobrevive a rotaciones
     * porque el ViewModel tambien, y no reinicia un cooldown ya consumido. */
    fun startInitialResendCooldown() {
        if (initialCooldownStarted) return
        initialCooldownStarted = true
        startResendCooldown()
    }

    private fun startResendCooldown() {
        resendCooldownJob?.cancel()
        resendCooldownJob = viewModelScope.launch {
            for (remaining in RESEND_CODE_COOLDOWN_SECONDS downTo 1) {
                _resendCooldownSeconds.value = remaining
                delay(1000)
            }
            _resendCooldownSeconds.value = 0
        }
    }

    /** Verifica el código que se envió por email y fija la contraseña nueva. Mismo
     * isSubmitting que el resto (no un flag aparte) -- bloquea doble tap igual
     * que signUp/signIn/sendPasswordReset. */
    fun confirmPasswordReset(email: String, code: String, newPassword: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                confirmPasswordResetUseCase(email, code, newPassword)
                _codeVerified.value = false
                _messageEvent.emit("Contraseña actualizada")
                _passwordResetCompletedEvent.emit(Unit)
            } catch (e: IllegalArgumentException) {
                _messageEvent.emit(e.message ?: "Error al actualizar la contraseña")
            } catch (e: Exception) {
                println("Error al confirmar el restablecimiento de contraseña: $e")
                _codeVerified.value = confirmPasswordResetUseCase.isCodeVerified(email)
                _messageEvent.emit(e.toAuthErrorMessage())
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
