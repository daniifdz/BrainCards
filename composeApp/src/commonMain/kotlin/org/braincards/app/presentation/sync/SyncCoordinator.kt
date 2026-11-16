package org.braincards.app.presentation.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AUTO_SYNC_ENABLED_KEY
import org.braincards.app.data.local.LAST_SUCCESSFUL_SYNC_KEY
import org.braincards.app.data.local.SYNC_PREFS_KEYS
import org.braincards.app.data.local.autoSyncEnabledFlow
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.LocalDataRepository
import org.braincards.app.domain.repository.PendingChangesObserver
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
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val pauseGate: AutoSyncPauseGate = AutoSyncPauseGate(),
    private val clock: Clock = Clock.System,
    private val pendingObserver: PendingChangesObserver? = null
) : AutoSyncHost {
    private val scope = coroutineScope
    private val syncMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSuccessfulSyncAt = MutableStateFlow<Instant?>(null)
    val lastSuccessfulSyncAt: StateFlow<Instant?> = _lastSuccessfulSyncAt.asStateFlow()

    // _lastSuccessfulSyncAt es null unos instantes al arrancar (se lee del DataStore en init): este indicador pasa a
    // true cuando la lectura TERMINA (con valor, sin valor o con fallo) para distinguir "aun no leido" de "nunca".
    private val _lastSuccessfulSyncAtLoaded = MutableStateFlow(false)
    val lastSuccessfulSyncAtLoaded: StateFlow<Boolean> = _lastSuccessfulSyncAtLoaded.asStateFlow()

    // Con [pendingObserver] el contador es REACTIVO: lo alimenta el observador (incluye las filas sin dueno, creadas
    // sin conexion y aun sin adoptar, que getPendingSync no ve). Sin observador (solo tests antiguos) se recalcula
    // al cambiar la sesion y al acabar un sync.
    private val _pendingChangesCount = MutableStateFlow(0)
    val pendingChangesCount: StateFlow<Int> = _pendingChangesCount.asStateFlow()

    // Sube cada vez que termina un cierre de sesion o un borrado de cuenta del USUARIO (los dos llaman a
    // resetSyncState al acabar bien). El arranque lo usa para NO esperar al dar por cerrada la sesion.
    private val _userSessionExitCount = MutableStateFlow(0)
    val userSessionExitCount: StateFlow<Int> = _userSessionExitCount.asStateFlow()

    // Motivo de la ULTIMA salida del usuario. resetSyncState lo escribe ANTES de subir el contador: quien vea el
    // contador nuevo lee ya el motivo correcto. null = el reset no fue una salida confirmada (SessionCloseFailed: la
    // sesion sigue viva) o aun no hubo ninguna.
    private val _userSessionExitReason = MutableStateFlow<SessionExitReason?>(null)
    val userSessionExitReason: StateFlow<SessionExitReason?> = _userSessionExitReason.asStateFlow()

    // "Salida esperada": el usuario ya confirmo salir y la peticion esta EN CURSO. La sesion puede dejar de existir
    // (NotAuthenticated) mucho antes de que termine el cierre/borrado (red lenta); sin esta marca el arranque lo
    // tomaria por una sesion perdida. Se pone justo antes de la llamada y se limpia en resetSyncState, o aqui mismo si
    // la llamada falla, se cancela o no cierra la sesion.
    private val _expectedUserExit = MutableStateFlow<SessionExitReason?>(null)
    val expectedUserExit: StateFlow<SessionExitReason?> = _expectedUserExit.asStateFlow()

    fun markExpectedUserExit(reason: SessionExitReason) {
        _expectedUserExit.value = reason
    }

    fun clearExpectedUserExit() {
        _expectedUserExit.value = null
    }

    // Resultado del ULTIMO sync, venga de donde venga (manual, completo o automatico): lo escribe syncLocked.
    private val _lastOutcome = MutableStateFlow<SyncOutcome?>(null)
    override val lastOutcome: StateFlow<SyncOutcome?> = _lastOutcome.asStateFlow()

    /** Estado observable de la sincronizacion (silencioso: sin textos ni carteles). Lo mostrara el rediseño. */
    val status: StateFlow<SyncStatus> = combine(
        authRepository.authState, _isSyncing, _lastOutcome, _lastSuccessfulSyncAt
    ) { auth, syncing, outcome, lastSuccess -> deriveSyncStatus(auth, syncing, outcome, lastSuccess) }
        .stateIn(scope, SharingStarted.Eagerly, SyncStatus.SignedOut)

    // Lease SIGN_OUT del cierre de sesion en curso (prepareSignOut -> finishSignOut/abortSignOut). Uno solo.
    private var signOutLease: AutoSyncPauseGate.Lease? = null

    // Suscripciones propias del coordinador: close() las cancela (el scope es inyectado y no es nuestro).
    private var authJob: Job? = null
    private var pendingJob: Job? = null

    init {
        scope.launch {
            try {
                _lastSuccessfulSyncAt.value = readLastSuccessfulSyncAt()
            } finally {
                _lastSuccessfulSyncAtLoaded.value = true
            }
        }
        authJob = scope.launch {
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
                        if (pendingObserver == null) refreshPendingChangesCount(state.userId)
                        // El sync de arranque / inicio de sesion ya NO se lanza aqui: lo dispara el AutoSyncController
                        // (sesion confirmada + primer plano + red), que respeta el intervalo minimo y el backoff.
                    }
                    is AuthState.NotAuthenticated -> _pendingChangesCount.value = 0
                    else -> Unit // Loading
                }
            }
        }
        if (pendingObserver != null) {
            pendingJob = scope.launch {
                // Se re-suscribe al cambiar de usuario; sin sesion el contador es 0; en Loading conserva el valor.
                authRepository.authState
                    .distinctUntilChanged()
                    .flatMapLatest { state ->
                        when (state) {
                            is AuthState.Authenticated -> pendingObserver.observe(state.userId).map { it.count }
                            is AuthState.NotAuthenticated -> flowOf(0)
                            else -> emptyFlow()
                        }
                    }
                    .collect { _pendingChangesCount.value = it }
            }
        }
    }

    /** Cancela las suscripciones del coordinador (sesion y pendientes). Idempotente. */
    fun close() {
        pendingJob?.cancel()
        pendingJob = null
        authJob?.cancel()
        authJob = null
    }

    /** Disparador manual del boton "Sincronizar ahora" de Ajustes. La puerta de pausa NO lo bloquea. */
    suspend fun syncNow(): SyncResult = runSync()

    /**
     * Sync AUTOMATICO (lo pide el AutoSyncController). Toma el mismo Mutex que cualquier otro sync y, DENTRO del
     * lock, comprueba la sesion y la puerta de pausa: si entre la peticion y la toma del lock se cerro la sesion,
     * empezo un cierre o una sesion de estudio, no sincroniza y devuelve "omitido" con su motivo.
     */
    override suspend fun requestAutoSync(): AutoSyncOutcome = syncMutex.withLock {
        val state = authRepository.authState.first()
        if (state !is AuthState.Authenticated) return@withLock AutoSyncOutcome.Skipped(AutoSyncSkipReason.NO_SESSION)
        if (!state.sessionConfirmed) return@withLock AutoSyncOutcome.Skipped(AutoSyncSkipReason.SESSION_UNCONFIRMED)
        // Interruptor apagado: tambien aqui (no solo en el controlador) para cerrar la carrera entre pedir el sync y
        // tomar el lock. El sync MANUAL (syncNow) no pasa por aqui.
        if (!isAutoSyncEnabled()) return@withLock AutoSyncOutcome.Skipped(AutoSyncSkipReason.DISABLED)
        when (pauseGate.activeReason()) {
            PauseReason.SIGN_OUT -> AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_SIGN_OUT)
            PauseReason.STUDY -> AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_STUDY)
            null -> AutoSyncOutcome.Ran(syncLocked())
        }
    }

    // Ausente o ilegible (IOException / preferencias corruptas) = activado: la MISMA lectura que la preferencia.
    private suspend fun isAutoSyncEnabled(): Boolean = dataStore.autoSyncEnabledFlow().first()

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
    suspend fun prepareSignOut(): SignOutPrompt {
        // La puerta se cierra ANTES de esperar el Mutex: un sync automatico encolado detras ya vera el cierre en curso.
        holdSignOutLease()
        return try {
            prepareSignOutLocked()
        } catch (e: Throwable) {
            releaseSignOutLease()
            throw e
        }
    }

    private suspend fun prepareSignOutLocked(): SignOutPrompt = syncMutex.withLock {
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
    suspend fun finishSignOut(userId: String, confirmedCount: Int): FinishSignOutResult {
        val result = finishSignOutLocked(userId, confirmedCount)
        // Con NeedsReconfirmation el dialogo sigue abierto: la puerta sigue cerrada (renovada). En cualquier otro
        // resultado el cierre termino (o se descarto) y la sync automatica puede reanudarse.
        if (result is FinishSignOutResult.NeedsReconfirmation) signOutLease?.renew() else releaseSignOutLease()
        return result
    }

    /** "Cancelar" del dialogo, o el ViewModel del cierre destruido (onCleared): el cierre ya no sigue. Idempotente. */
    fun abortSignOut() {
        releaseSignOutLease()
    }

    private fun holdSignOutLease() {
        signOutLease?.release()
        signOutLease = pauseGate.acquire(PauseReason.SIGN_OUT)
    }

    private fun releaseSignOutLease() {
        signOutLease?.release()
        signOutLease = null
    }

    private suspend fun finishSignOutLocked(userId: String, confirmedCount: Int): FinishSignOutResult =
        syncMutex.withLock {
            val current = authRepository.authState.first()
            if ((current as? AuthState.Authenticated)?.userId != userId) {
                return@withLock FinishSignOutResult.Finished(SignOutAndWipeResult.SessionChanged)
            }
            val unsynced = countUnsynced(userId)
            if (unsynced > confirmedCount) {
                return@withLock FinishSignOutResult.NeedsReconfirmation(unsynced)
            }
            // Salida esperada: marcada justo ANTES de la llamada (la sesion puede caer antes de que termine).
            markExpectedUserExit(SessionExitReason.SignedOut)
            val outcome = try {
                signOutAndWipeUseCase(userId)
            } catch (e: Throwable) {
                clearExpectedUserExit() // fallo o cancelacion: no hay salida
                throw e
            }
            // La BD ya esta vacia en SignedOut y en SessionCloseFailed: el estado de sync
            // (ultima sync, pendientes) ya no describe nada. Con WipeFailed/SessionChanged no
            // se toco nada y se conserva. Solo SignedOut es una salida: con SessionCloseFailed la
            // sesion sigue viva (motivo null; el contador sube igual, como siempre).
            when (outcome) {
                SignOutAndWipeResult.SignedOut -> resetSyncState(SessionExitReason.SignedOut)
                is SignOutAndWipeResult.SessionCloseFailed -> resetSyncState()
                else -> clearExpectedUserExit()
            }
            FinishSignOutResult.Finished(outcome)
        }

    /**
     * Olvida el estado de sync: flows a null/0 y borra del DataStore, clave a clave, las de
     * [SYNC_PREFS_KEYS] (nunca clear(): app_theme y demas preferencias se conservan). No toma el
     * Mutex (lo llaman quienes ya lo tienen). Best-effort con el DataStore: si falla, el cierre
     * de sesion/borrado de cuenta que ya ocurrio no debe parecer fallido.
     */
    suspend fun resetSyncState(exitReason: SessionExitReason? = null) {
        // Orden: motivo, contador, y por ultimo se limpia la marca de "salida esperada" (nunca hay un hueco sin dato).
        _userSessionExitReason.value = exitReason
        _userSessionExitCount.value += 1
        _expectedUserExit.value = null
        _lastSuccessfulSyncAt.value = null
        _pendingChangesCount.value = 0
        _lastOutcome.value = null
        try {
            dataStore.edit { prefs ->
                SYNC_PREFS_KEYS.forEach { key -> prefs -= key }
                // Solo en una salida confirmada: SessionCloseFailed (exitReason null) deja el interruptor como estaba.
                if (exitReason != null) prefs -= AUTO_SYNC_ENABLED_KEY
            }
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
            val result = try {
                syncEngineUseCase(forceFullPull)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lastOutcome.value = SyncOutcome(result = null, at = clock.now())
                throw e
            }
            _lastOutcome.value = SyncOutcome(result, clock.now())
            if (result is SyncResult.Completed) logPullCounts(result)
            if (result is SyncResult.Completed && result.isFullySuccessful()) {
                val now = clock.now()
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
        if (pendingObserver != null) return // el observador es la unica fuente del contador
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
