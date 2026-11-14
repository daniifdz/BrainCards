package org.braincards.app.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.AuthRepository

/**
 * Indicador de sync para la UI. `null` = no dibujar: aun no hubo ningun AuthState estable (arranque en frio).
 * [SyncIndicator.Unknown] (lectura de la ultima sync correcta sin terminar) tampoco se dibuja: lo decide la UI.
 *
 * Tras el primer AuthState estable, un [AuthState.Loading] posterior (Android lo emite al volver de segundo plano)
 * NO oculta ni cambia nada: se conserva el ultimo estado estable. Un cambio real (Authenticated -> NotAuthenticated)
 * si se refleja. Solo vive aqui: ni AuthRepository ni AuthViewModel lo saben.
 */
class SyncStatusViewModel(
    authRepository: AuthRepository,
    syncCoordinator: SyncCoordinator,
    connectivityMonitor: ConnectivityMonitor
) : ViewModel() {

    private data class SyncSnapshot(
        val isSyncing: Boolean,
        val outcome: SyncOutcome?,
        val lastSuccessfulSyncAt: Instant?,
        val loaded: Boolean
    )

    // null hasta el primer estado estable; despues, el ultimo estable (Loading no lo pisa).
    private val stableAuth = authRepository.authState
        .scan<AuthState, AuthState?>(null) { lastStable, next -> if (next is AuthState.Loading) lastStable else next }
        .distinctUntilChanged()

    private val syncSnapshot = combine(
        syncCoordinator.isSyncing,
        syncCoordinator.lastOutcome,
        syncCoordinator.lastSuccessfulSyncAt,
        syncCoordinator.lastSuccessfulSyncAtLoaded
    ) { syncing, outcome, last, loaded -> SyncSnapshot(syncing, outcome, last, loaded) }

    // Eagerly (no WhileSubscribed): al volver a Home pasados unos segundos el flujo no se reinicia, asi que la
    // pildora no parpadea a null. Todas las fuentes son singletons ya vivos.
    val indicator: StateFlow<SyncIndicator?> = combine(
        stableAuth, connectivityMonitor.isOnline, syncSnapshot
    ) { auth, online, snap ->
        auth?.let {
            deriveSyncIndicator(it, online, snap.isSyncing, snap.outcome, snap.lastSuccessfulSyncAt, snap.loaded)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
