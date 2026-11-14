package org.braincards.app.presentation.screens.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.LocalModePreference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Espera antes de dar por perdida una sesion que cae SIN que el usuario lo haya pedido. Una caida real de supabase
 * no vuelve sola (borra la sesion guardada), pero el mapeo RefreshFailure -> NotAuthenticated puede ver un instante
 * "sin sesion guardada" y recuperarse (ver AuthStateMapper); la espera lo absorbe. El cierre de sesion y el borrado de
 * cuenta del usuario NO esperan: avisan por [userSessionExitCount].
 */
val SESSION_LOSS_DEBOUNCE: Duration = 1_500.milliseconds

/**
 * Arranque y sesion perdida (paso 4). Activity-scoped (como ThemeViewModel): sobrevive a rotar.
 *
 *  - [startRoute]: ruta de arranque, FIJADA con el primer valor resuelto (no cambia despues: lo posterior son eventos).
 *  - [signedOut]: se emite UNA vez cuando una sesion estable pasa a NotAuthenticated (cierre de sesion, borrado de
 *    cuenta, sesion revocada). Antes de emitirlo se borra el flag de modo local. Es un Channel: cada evento lo consume
 *    un solo colector y no se repite al recomponer ni al rotar.
 *  - [userSessionExitCount]: contador que sube cuando un cierre de sesion / borrado de cuenta del USUARIO termina
 *    (SyncCoordinator.userSessionExitCount): entonces no hay espera.
 */
class AppEntryViewModel(
    authRepository: AuthRepository,
    private val localModePreference: LocalModePreference,
    private val userSessionExitCount: StateFlow<Int>
) : ViewModel() {

    private val stableAuth: Flow<AuthState?> = authRepository.authState.stableAuth()

    private val route: StateFlow<EntryRoute> = combine(
        stableAuth,
        localModePreference.chosen.map<Boolean, Boolean?> { it }.onStart { emit(null) }
    ) { auth, chosen -> resolveStartDestination(auth, chosen) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EntryRoute.Pending)

    private val _startRoute = MutableStateFlow(EntryRoute.Pending)
    val startRoute: StateFlow<EntryRoute> = _startRoute

    private val signedOutChannel = Channel<Unit>(Channel.BUFFERED)
    val signedOut: Flow<Unit> = signedOutChannel.receiveAsFlow()

    // Ultimo estado estable visto y valor del contador cuando estaba autenticado.
    private var lastStable: AuthState? = null
    private var exitCountWhileAuthenticated = 0

    init {
        viewModelScope.launch {
            route.collect { resolved ->
                if (_startRoute.value == EntryRoute.Pending && resolved != EntryRoute.Pending) _startRoute.value = resolved
            }
        }
        viewModelScope.launch {
            stableAuth.filterNotNull().collectLatest { state ->
                val before = lastStable
                lastStable = state
                when (state) {
                    is AuthState.Authenticated -> exitCountWhileAuthenticated = userSessionExitCount.value
                    AuthState.NotAuthenticated -> if (before is AuthState.Authenticated) onSessionLost()
                    AuthState.Loading -> Unit // nunca llega: stableAuth lo filtra
                }
            }
        }
    }

    // collectLatest cancela esta espera si la sesion vuelve (parpadeo): no se navega ni se toca el flag.
    private suspend fun onSessionLost() {
        val seen = exitCountWhileAuthenticated
        if (userSessionExitCount.value <= seen) {
            withTimeoutOrNull(SESSION_LOSS_DEBOUNCE) { userSessionExitCount.first { it > seen } }
        }
        // Ya sin posible cancelacion: el flag se borra ANTES de avisar, y siempre se avisa tras borrarlo.
        withContext(NonCancellable) {
            localModePreference.setChosen(false)
            signedOutChannel.send(Unit)
        }
    }

    /** «Continuar sin cuenta» en la Bienvenida de entrada: de ahora en adelante la app arranca en Home sin sesion. */
    suspend fun chooseLocalMode() {
        localModePreference.setChosen(true)
    }
}
