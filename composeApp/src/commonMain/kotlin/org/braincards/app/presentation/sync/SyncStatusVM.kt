package org.braincards.app.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.datetime.Instant
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.presentation.screens.navigation.stableAuth
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Gracia (SOLO de presentacion) con la que se retrasa un error de red al recuperar la conexion. El auto-sync de la
 * reconexion es inmediato (no usa el debounce de 5 s, que es solo de cambios locales), asi que el resultado viejo
 * aparece solo un instante; 3 s cubren el arranque del sync sin esconder un fallo real mas que eso. Si el intervalo
 * minimo (30 s) aplaza el sync, el error se muestra al acabar la gracia.
 */
val SYNC_ERROR_GRACE_AFTER_RECONNECT: Duration = 3000.milliseconds

/**
 * Cuanto falta de gracia para mostrar [indicator]. [sinceReconnect] = tiempo desde que volvio la conexion (null = no
 * hubo reconexion reciente o esta sin red). Solo [SyncErrorKind.FAILED] y [SyncErrorKind.PUSH_PARTIAL] (pueden venir
 * del intento sin red) esperan; LOCAL y REJECTED no son de red y se muestran sin gracia. Funcion pura.
 */
internal fun errorGraceRemaining(indicator: SyncIndicator?, sinceReconnect: Duration?, grace: Duration): Duration {
    if (indicator !is SyncIndicator.Error || sinceReconnect == null) return Duration.ZERO
    if (indicator.kind != SyncErrorKind.FAILED && indicator.kind != SyncErrorKind.PUSH_PARTIAL) return Duration.ZERO
    return (grace - sinceReconnect).coerceAtLeast(Duration.ZERO)
}

/**
 * Indicador de sync para la UI. `null` = no dibujar: aun no hubo ningun AuthState estable (arranque en frio).
 * [SyncIndicator.Unknown] (lectura de la ultima sync correcta sin terminar) tampoco se dibuja: lo decide la UI.
 *
 * Tras el primer AuthState estable, un [AuthState.Loading] posterior (Android lo emite al volver de segundo plano)
 * NO oculta ni cambia nada: se conserva el ultimo estado estable. Un cambio real (Authenticated -> NotAuthenticated)
 * si se refleja. Solo vive aqui: ni AuthRepository ni AuthViewModel lo saben.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusViewModel(
    authRepository: AuthRepository,
    syncCoordinator: SyncCoordinator,
    connectivityMonitor: ConnectivityMonitor,
    timeSource: TimeSource = TimeSource.Monotonic,
    errorGrace: Duration = SYNC_ERROR_GRACE_AFTER_RECONNECT
) : ViewModel() {

    private data class Link(val online: Boolean, val reconnectedAt: TimeMark?)

    private data class Raw(val indicator: SyncIndicator?, val reconnectedAt: TimeMark?)

    private data class SyncSnapshot(
        val isSyncing: Boolean,
        val outcome: SyncOutcome?,
        val lastSuccessfulSyncAt: Instant?,
        val loaded: Boolean
    )

    // null hasta el primer estado estable; despues, el ultimo estable (Loading no lo pisa). Compartido con el arranque.
    private val stableAuth = authRepository.authState.stableAuth()

    private val syncSnapshot = combine(
        syncCoordinator.isSyncing,
        syncCoordinator.lastOutcome,
        syncCoordinator.lastSuccessfulSyncAt,
        syncCoordinator.lastSuccessfulSyncAtLoaded
    ) { syncing, outcome, last, loaded -> SyncSnapshot(syncing, outcome, last, loaded) }

    // Eagerly (no WhileSubscribed): al volver a Home pasados unos segundos el flujo no se reinicia, asi que la
    // pildora no parpadea a null. Todas las fuentes son singletons ya vivos.
    // Conectividad + instante de la ULTIMA reconexion (false -> true). Al caer la red se borra.
    private val link = connectivityMonitor.isOnline
        .scan(null as Link?) { prev, on ->
            Link(on, if (on && prev?.online == false) timeSource.markNow() else if (on) prev?.reconnectedAt else null)
        }
        .filterNotNull()

    private val raw = combine(stableAuth, link, syncSnapshot) { auth, link, snap ->
        Raw(
            indicator = auth?.let {
                deriveSyncIndicator(it, link.online, snap.isSyncing, snap.outcome, snap.lastSuccessfulSyncAt, snap.loaded)
            },
            reconnectedAt = link.reconnectedAt
        )
    }

    // Gracia solo de presentacion: el estado real (coordinador, motor, deriveSyncIndicator) no cambia. Durante la
    // gracia se sigue mostrando Offline; si llega Syncing/Synced antes, el error no se ve nunca.
    val indicator: StateFlow<SyncIndicator?> = raw
        .transformLatest { (indicator, reconnectedAt) ->
            val remaining = errorGraceRemaining(indicator, reconnectedAt?.elapsedNow(), errorGrace)
            if (remaining > Duration.ZERO) {
                emit(SyncIndicator.Offline)
                delay(remaining)
            }
            emit(indicator)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
