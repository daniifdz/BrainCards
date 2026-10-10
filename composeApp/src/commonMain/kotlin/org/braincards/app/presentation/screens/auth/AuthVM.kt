package org.braincards.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.usecase.Auth.DeleteAccountUseCase
import org.braincards.app.domain.usecase.Auth.SendPasswordResetUseCase
import org.braincards.app.domain.usecase.Auth.SignInUseCase
import org.braincards.app.domain.usecase.Auth.SignOutUseCase
import org.braincards.app.domain.usecase.Auth.SignUpUseCase
import org.braincards.app.domain.usecase.Sync.SyncResult
import org.braincards.app.presentation.sync.SyncCoordinator

class AuthViewModel(
    authRepository: AuthRepository,
    private val signUpUseCase: SignUpUseCase,
    private val signInUseCase: SignInUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val sendPasswordResetUseCase: SendPasswordResetUseCase,
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
            _messageEvent.emit(result.toUserMessage())
        }
    }

    private fun SyncResult.toUserMessage(): String = when (this) {
        is SyncResult.NotAuthenticated -> "Inicia sesión para sincronizar"
        is SyncResult.Completed -> {
            val pullAbortedCount = listOf(pull.decks, pull.topics, pull.flashcards).count { it.aborted }
            val pushFailedCount = push.decks.failed + push.topics.failed + push.flashcards.failed
            when {
                pullAbortedCount > 0 && pushFailedCount > 0 ->
                    "No se pudo sincronizar del todo. Se reintentará más tarde"
                pullAbortedCount > 0 ->
                    "No se pudieron comprobar los cambios remotos. Se reintentará más tarde"
                pushFailedCount > 0 ->
                    "Sincronizado parcialmente: $pushFailedCount cambios pendientes de subir"
                else -> "Sincronizado"
            }
        }
    }

    fun signUp(email: String, password: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                signUpUseCase(email, password)
            } catch (e: IllegalArgumentException) {
                _messageEvent.emit(e.message ?: "Error al registrarse")
            } catch (e: Exception) {
                println("Error al registrarse: $e")
                _messageEvent.emit("No se pudo completar el registro")
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
                _messageEvent.emit("No se pudo iniciar sesión. Revisa tu email y contraseña")
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                signOutUseCase()
            } catch (e: Exception) {
                println("Error al cerrar sesión: $e")
                _messageEvent.emit("No se pudo cerrar sesión")
            }
        }
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

    fun sendPasswordReset(email: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                sendPasswordResetUseCase(email)
                _messageEvent.emit("Te hemos enviado un correo para restablecer tu contraseña")
            } catch (e: IllegalArgumentException) {
                _messageEvent.emit(e.message ?: "Error al enviar el correo")
            } catch (e: Exception) {
                println("Error al enviar el correo de recuperación: $e")
                _messageEvent.emit("No se pudo enviar el correo")
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
