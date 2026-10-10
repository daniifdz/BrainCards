package org.braincards.app.presentation.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.TopicsRepository
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import org.braincards.app.domain.usecase.Sync.SyncResult
import org.braincards.app.domain.usecase.Sync.isFullySuccessful

private val LAST_SUCCESSFUL_SYNC_KEY = stringPreferencesKey("last_successful_sync_at")

/**
 * Coordinador unico de sync para toda la app (Bloque D: "sin conexion y
 * reintentos" + sincronizacion al arrancar). Registrado en Koin como
 * single(createdAtStart = true) -- NO como ViewModel, a proposito: si fuera un
 * ViewModel de Koin, AuthViewModel (que si es per-pantalla, ver Navigation Compose)
 * tendria que recibirlo inyectado para compartir el Mutex, y seguiriamos sin un
 * Mutex realmente unico. Como `single`, Koin devuelve siempre la misma instancia
 * sin importar desde que pantalla se pida, y createdAtStart fuerza la instanciacion
 * al arrancar la app, no en la primera visita a Ajustes.
 *
 * [coroutineScope] es inyectable para poder sustituirlo por un scope con
 * Dispatchers.Unconfined en tests (ejecucion deterministica sin
 * kotlinx-coroutines-test); en produccion usa su propio scope de vida igual a la
 * del proceso (no hay un "onCleared" real para un single de Koin).
 */
class SyncCoordinator(
    private val authRepository: AuthRepository,
    private val syncEngineUseCase: SyncEngineUseCase,
    private val decksRepository: DecksRepository,
    private val topicsRepository: TopicsRepository,
    private val flashcardRepository: FlashcardRepository,
    private val dataStore: DataStore<Preferences>,
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val scope = coroutineScope
    private val syncMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSuccessfulSyncAt = MutableStateFlow<Instant?>(null)
    val lastSuccessfulSyncAt: StateFlow<Instant?> = _lastSuccessfulSyncAt.asStateFlow()

    private val _pendingChangesCount = MutableStateFlow(0)
    val pendingChangesCount: StateFlow<Int> = _pendingChangesCount.asStateFlow()

    init {
        scope.launch {
            _lastSuccessfulSyncAt.value = readLastSuccessfulSyncAt()
        }
        scope.launch {
            // distinctUntilChanged: auth-kt emite un SessionStatus.Authenticated
            // nuevo en cada refresco automatico del token (cada hora aprox.) -- sin
            // esto, cada refresco cancelaria (collectLatest) un sync en curso y
            // lanzaria uno nuevo sin necesidad, porque AuthState.Authenticated es
            // data class y no cambia entre esos refrescos (mismo userId/email/
            // sessionConfirmed).
            authRepository.authState.distinctUntilChanged().collectLatest { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        // Tambien sin confirmar (offline): consulta solo local, sin
                        // red, para no mostrar "0 pendientes" en un arranque en frio
                        // sin conexion.
                        refreshPendingChangesCount(state.userId)
                        if (state.sessionConfirmed) {
                            runSync()
                        }
                    }
                    is AuthState.NotAuthenticated -> _pendingChangesCount.value = 0
                    else -> Unit // Loading
                }
            }
        }
    }

    /** Disparador manual del boton "Sincronizar ahora" de Ajustes. */
    suspend fun syncNow(): SyncResult = runSync()

    /** Expone el Mutex sin filtrar el objeto en si -- lo usa
     * AuthViewModel.deleteAccount() para no correr a la vez que un sync. */
    suspend fun <T> withSyncLock(block: suspend () -> T): T = syncMutex.withLock { block() }

    private suspend fun runSync(): SyncResult = syncMutex.withLock {
        _isSyncing.value = true
        try {
            // syncEngineUseCase() ya ejecuta el backfill como primer paso de cada
            // llamada (ver SyncEngineUseCase.invoke) -- no hace falta repetirlo aqui.
            val result = syncEngineUseCase()
            if (result is SyncResult.Completed && result.isFullySuccessful()) {
                val now = Clock.System.now()
                _lastSuccessfulSyncAt.value = now
                persistLastSuccessfulSyncAt(now)
            }
            val userId = (authRepository.authState.first() as? AuthState.Authenticated)?.userId
            refreshPendingChangesCount(userId)
            result
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun refreshPendingChangesCount(userId: String?) {
        _pendingChangesCount.value = if (userId == null) {
            0
        } else {
            decksRepository.getPendingSync(userId).size +
                topicsRepository.getPendingSync(userId).size +
                flashcardRepository.getPendingSync(userId).size
        }
    }

    private suspend fun readLastSuccessfulSyncAt(): Instant? =
        dataStore.data.first()[LAST_SUCCESSFUL_SYNC_KEY]?.let { Instant.parse(it) }

    private suspend fun persistLastSuccessfulSyncAt(instant: Instant) {
        dataStore.edit { prefs -> prefs[LAST_SUCCESSFUL_SYNC_KEY] = instant.toString() }
    }
}
