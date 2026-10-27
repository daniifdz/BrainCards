package org.braincards.app.presentation.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.data.local.LAST_SUCCESSFUL_SYNC_KEY
import org.braincards.app.data.local.SYNC_PREFS_KEYS
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.LocalDataRepository
import org.braincards.app.domain.repository.TopicsRepository
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeResult
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import org.braincards.app.domain.usecase.Sync.SyncResult
import org.braincards.app.domain.usecase.Sync.isFullySuccessful
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.braincards.app.util.logError

/**
 * Tiempo maximo del sync previo al cierre de sesion. Si se agota (o falla), se sigue con el
 * conteo local: el usuario nunca espera indefinidamente para poder cerrar sesion.
 */
val SYNC_BEFORE_SIGN_OUT_TIMEOUT: Duration = 12.seconds

/** Resultado de [SyncCoordinator.prepareSignOut]: lo que la UI debe hacer a continuacion. */
sealed interface SignOutPrompt {
    /** Nada que perder: se puede llamar a finishSignOut directamente (sin dialogo). */
    data object ReadyToSignOut : SignOutPrompt

    /** Hay [unsyncedChanges] cambios sin sincronizar que se perderian: pedir confirmacion. */
    data class NeedsConfirmation(val unsyncedChanges: Int) : SignOutPrompt
}

/** Resultado de [SyncCoordinator.finishSignOut]. */
sealed interface FinishSignOutResult {
    /** Se intento el cierre; [outcome] dice como acabo (SignedOut, SessionChanged, WipeFailed...). */
    data class Finished(val outcome: SignOutAndWipeResult) : FinishSignOutResult

    /** Entraron mas cambios sin sincronizar que los confirmados: NO se borro nada. */
    data class NeedsReconfirmation(val unsyncedChanges: Int) : FinishSignOutResult
}

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
    private val cardSchedulingRepository: CardSchedulingRepository,
    private val dataStore: DataStore<Preferences>,
    private val localDataRepository: LocalDataRepository,
    private val signOutAndWipeUseCase: SignOutAndWipeUseCase,
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

    /**
     * Cierre de sesion, paso 1: sincroniza (con tiempo limite) y cuenta lo que se perderia.
     * Toma el Mutex SOLO durante el sync previo y el conteo, y lo suelta al devolver: nunca se
     * mantiene mientras la UI muestra el dialogo de confirmacion. NO borra nada.
     *
     * Sin sesion confirmada (arranque en frio sin red) o sin sesion no se intenta el sync: solo
     * conteo local. Si el sync falla o se agota, tambien se sigue con el conteo local.
     */
    suspend fun prepareSignOut(): SignOutPrompt = syncMutex.withLock {
        val state = authRepository.authState.first()
        val userId = (state as? AuthState.Authenticated)?.userId
        if (state is AuthState.Authenticated && state.sessionConfirmed) {
            try {
                withTimeoutOrNull(SYNC_BEFORE_SIGN_OUT_TIMEOUT) { syncLocked() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("El sync previo al cierre de sesion fallo, se sigue con el conteo local", e)
            }
        }
        val unsynced = countUnsynced(userId)
        if (unsynced == 0) SignOutPrompt.ReadyToSignOut else SignOutPrompt.NeedsConfirmation(unsynced)
    }

    /**
     * Cierre de sesion, paso 2 (tras confirmar, o directamente si prepareSignOut dio
     * ReadyToSignOut). Toma el Mutex, recuenta y, si entraron mas cambios que
     * [confirmedCount], devuelve NeedsReconfirmation SIN borrar. Si no, ejecuta
     * SignOutAndWipeUseCase manteniendo el Mutex hasta que termina (clearSession incluido):
     * un sync lanzado entre el borrado y el cierre de sesion esperaria y no repoblaria la BD.
     */
    suspend fun finishSignOut(userId: String, confirmedCount: Int): FinishSignOutResult =
        syncMutex.withLock {
            val current = authRepository.authState.first()
            if ((current as? AuthState.Authenticated)?.userId != userId) {
                return@withLock FinishSignOutResult.Finished(SignOutAndWipeResult.SessionChanged)
            }
            val unsynced = countUnsynced(userId)
            if (unsynced > confirmedCount) {
                return@withLock FinishSignOutResult.NeedsReconfirmation(unsynced)
            }
            val outcome = signOutAndWipeUseCase(userId)
            // La BD ya esta vacia en SignedOut y en SessionCloseFailed: el estado de sync
            // (ultima sync, pendientes) ya no describe nada. Con WipeFailed/SessionChanged no
            // se toco nada y se conserva.
            if (outcome is SignOutAndWipeResult.SignedOut || outcome is SignOutAndWipeResult.SessionCloseFailed) {
                resetSyncState()
            }
            FinishSignOutResult.Finished(outcome)
        }

    /**
     * Olvida el estado de sync: flows a null/0 y borra del DataStore, clave a clave, las de
     * [SYNC_PREFS_KEYS] (nunca clear(): app_theme y demas preferencias se conservan). No toma el
     * Mutex (lo llaman quienes ya lo tienen). Best-effort con el DataStore: si falla, el cierre
     * de sesion/borrado de cuenta que ya ocurrio no debe parecer fallido.
     */
    suspend fun resetSyncState() {
        _lastSuccessfulSyncAt.value = null
        _pendingChangesCount.value = 0
        try {
            dataStore.edit { prefs -> SYNC_PREFS_KEYS.forEach { key -> prefs -= key } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("No se pudo borrar el estado de sync del DataStore", e)
        }
    }

    private suspend fun runSync(): SyncResult = syncMutex.withLock { syncLocked() }

    /**
     * Sync con pull COMPLETO de las cuatro tablas (ignora las marcas guardadas). Sin boton en Ajustes
     * todavia: lo usara el rediseño. Mismo Mutex que cualquier otro sync.
     */
    suspend fun requestFullPull(): SyncResult = syncMutex.withLock { syncLocked(forceFullPull = true) }

    /** Cuerpo de runSync SIN tomar el Mutex: el caller ya lo tiene (Mutex no reentrante). */
    private suspend fun syncLocked(forceFullPull: Boolean = false): SyncResult {
        _isSyncing.value = true
        return try {
            // syncEngineUseCase() ya ejecuta el backfill como primer paso de cada
            // llamada (ver SyncEngineUseCase.invoke) -- no hace falta repetirlo aqui.
            val result = syncEngineUseCase(forceFullPull)
            if (result is SyncResult.Completed) logPullCounts(result)
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

    // Solo conteos por tabla y modo: nada de datos de usuario, ids, tokens ni mensajes de excepcion.
    private fun logPullCounts(result: SyncResult.Completed) {
        val pull = result.pull
        val tables = listOf(pull.decks, pull.topics, pull.flashcards, pull.cardScheduling)
        val mode = if (tables.all { it.fullPull }) "completo" else "incremental"
        println(
            "Sync: bajadas decks=${pull.decks.downloaded} topics=${pull.topics.downloaded} " +
                "flashcards=${pull.flashcards.downloaded} scheduling=${pull.cardScheduling.downloaded} ($mode)"
        )
    }

    private suspend fun refreshPendingChangesCount(userId: String?) {
        _pendingChangesCount.value = if (userId == null) 0 else countPending(userId)
    }

    // Tombstones incluidos: getPendingSync no filtra deletedAt (un borrado sin subir es un
    // cambio que se perderia).
    private suspend fun countPending(userId: String): Int =
        decksRepository.getPendingSync(userId).size +
            topicsRepository.getPendingSync(userId).size +
            flashcardRepository.getPendingSync(userId).size +
            cardSchedulingRepository.getPendingSync(userId).size

    /**
     * Lo que se perderia al borrar la BD local: pendientes de subir de [userId] + filas sin dueno
     * (userId NULL, creadas desde el ultimo sync y aun sin adoptar: getPendingSync no las ve).
     * Sin sesion no hay wipe, asi que no hay nada que perder (0).
     */
    private suspend fun countUnsynced(userId: String?): Int =
        if (userId == null) 0 else countPending(userId) + localDataRepository.countUnownedRows()

    private suspend fun readLastSuccessfulSyncAt(): Instant? =
        dataStore.data.first()[LAST_SUCCESSFUL_SYNC_KEY]?.let { Instant.parse(it) }

    private suspend fun persistLastSuccessfulSyncAt(instant: Instant) {
        dataStore.edit { prefs -> prefs[LAST_SUCCESSFUL_SYNC_KEY] = instant.toString() }
    }
}
