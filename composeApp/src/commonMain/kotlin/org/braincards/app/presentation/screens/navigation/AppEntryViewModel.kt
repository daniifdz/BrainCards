package org.braincards.app.presentation.screens.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.braincards.app.presentation.sync.SessionExitReason
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Aviso que muestra la Bienvenida tras salir de la sesion. Vive SOLO en memoria (en [AppEntryViewModel]); nunca va
 * en la ruta de navegacion ni en disco.
 *
 *  - [SignedOut] / [AccountDeleted]: el usuario lo pidio y lo confirmo -> Snackbar de un solo uso.
 *  - [SessionExpired]: la sesion se perdio sola -> aviso persistente sobre el formulario; [email] es el del ultimo
 *    estado autenticado visto en ESTE proceso (puede ser null). Su toString no lo incluye (nunca a logs).
 */
sealed interface WelcomeNotice {
    data object SignedOut : WelcomeNotice
    data object AccountDeleted : WelcomeNotice
    class SessionExpired(val email: String?) : WelcomeNotice {
        override fun toString(): String = "SessionExpired"
    }
}

/**
 * Espera antes de dar por perdida una sesion que cae SIN que el usuario lo haya pedido. Una caida real de supabase
 * no vuelve sola (borra la sesion guardada), pero el mapeo RefreshFailure -> NotAuthenticated puede ver un instante
 * "sin sesion guardada" y recuperarse (ver AuthStateMapper); la espera lo absorbe. El cierre de sesion y el borrado de
 * cuenta del usuario NO esperan: avisan por [userSessionExitCount] + [userExitReason], o por la marca
 * [expectedUserExit] (el cierre esta en curso aunque la sesion ya haya caido).
 */
val SESSION_LOSS_DEBOUNCE: Duration = 1_500.milliseconds

/**
 * Arranque y sesión perdida. Activity-scoped (como ThemeViewModel): sobrevive a rotar.
 *
 * - [startRoute]: ruta de arranque, fijada con el primer valor resuelto (no cambia después: lo posterior son eventos).
 * - [signedOut]: se emite una vez cuando una sesión estable pasa a NotAuthenticated (cierre de sesión, borrado de cuenta,
 *   sesión revocada). Antes de emitirlo se borra el flag de modo local. Es un Channel: cada evento lo consume un solo
 *   colector y no se repite al recomponer ni al rotar.
 * - [userSessionExitCount]: contador que sube cuando un cierre de sesión o borrado de cuenta del usuario termina
 *   (SyncCoordinator.userSessionExitCount): entonces no hay espera.
 * - [userExitReason]: motivo de esa salida (null = no fue una salida confirmada, p. ej. SessionCloseFailed: cuenta como sesión
 *   perdida, con espera).
 * - [welcomeNotice]: aviso de un solo uso para la Bienvenida (ver [WelcomeNotice]). Se fija antes de emitir [signedOut], así la
 *   primera composición de la Bienvenida ya lo ve. Activity-scoped: sobrevive a rotar; si el proceso muere desaparece
 *   (correcto: no se reconstruye ningún aviso).
 */
class AppEntryViewModel(
    authRepository: AuthRepository,
    private val localModePreference: LocalModePreference,
    private val userSessionExitCount: StateFlow<Int>,
    private val userExitReason: StateFlow<SessionExitReason?> = MutableStateFlow(null),
    private val expectedUserExit: StateFlow<SessionExitReason?> = MutableStateFlow(null)
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

    private val _welcomeNotice = MutableStateFlow<WelcomeNotice?>(null)
    val welcomeNotice: StateFlow<WelcomeNotice?> = _welcomeNotice.asStateFlow()

    /**
     * Consumo unico del aviso en forma de Snackbar: `true` solo para quien lo saca del slot (compareAndSet atomico);
     * cualquier otro intento (recomponer, rotar, otra pantalla) recibe `false` y no lo muestra.
     */
    fun consumeWelcomeNotice(notice: WelcomeNotice): Boolean = _welcomeNotice.compareAndSet(notice, null)

    /** El usuario interactuo con el formulario (o salio de la Bienvenida): el aviso persistente se descarta. */
    fun dismissWelcomeNotice() {
        _welcomeNotice.value = null
    }

    // Ultimo estado estable visto y valor del contador cuando estaba autenticado.
    private var lastStable: AuthState? = null
    // Correo del ultimo estado autenticado VISTO EN ESTE PROCESO, para precargarlo si la sesion se pierde sola. Solo
    // memoria: ni disco ni logs. Se suelta al usarlo.
    private var lastAuthenticatedEmail: String? = null
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
                    is AuthState.Authenticated -> {
                        exitCountWhileAuthenticated = userSessionExitCount.value
                        lastAuthenticatedEmail = state.email
                        _welcomeNotice.value = null // ya hay sesion: ningun aviso de salida sigue vigente
                    }
                    AuthState.NotAuthenticated -> if (before is AuthState.Authenticated) onSessionLost()
                    AuthState.Loading -> Unit // nunca llega: stableAuth lo filtra
                }
            }
        }
    }

    // collectLatest cancela esta espera si la sesion vuelve (parpadeo): no se navega ni se toca el flag.
    private suspend fun onSessionLost() {
        val seen = exitCountWhileAuthenticated
        val email = lastAuthenticatedEmail
        // Salida del USUARIO: la marca de "salida esperada" (cierre/borrado en curso) o un contador nuevo CON motivo.
        // Si ya se cumple no hay espera; si no, se espera el debounce. Sin ninguna de las dos = sesion perdida.
        val exitReason = withTimeoutOrNull(SESSION_LOSS_DEBOUNCE) {
            combine(expectedUserExit, userSessionExitCount, userExitReason) { expected, count, reason ->
                expected ?: if (count > seen) reason else null
            }.filterNotNull().first()
        }
        // Ya sin posible cancelacion: el flag se borra ANTES de avisar, y siempre se avisa tras borrarlo.
        withContext(NonCancellable) {
            localModePreference.setChosen(false)
            lastAuthenticatedEmail = null
            _welcomeNotice.value = when (exitReason) {
                SessionExitReason.SignedOut -> WelcomeNotice.SignedOut
                SessionExitReason.AccountDeleted -> WelcomeNotice.AccountDeleted
                null -> WelcomeNotice.SessionExpired(email)
            }
            signedOutChannel.send(Unit)
        }
    }

    /** «Continuar sin cuenta» en la Bienvenida de entrada: de ahora en adelante la app arranca en Home sin sesion. */
    suspend fun chooseLocalMode() {
        _welcomeNotice.value = null
        localModePreference.setChosen(true)
    }
}
