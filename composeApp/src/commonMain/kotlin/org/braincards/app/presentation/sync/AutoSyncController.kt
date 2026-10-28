package org.braincards.app.presentation.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.PendingStamp
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.PendingChangesObserver
import org.braincards.app.domain.usecase.Sync.SyncResult
import org.braincards.app.domain.usecase.Sync.isFullySuccessful
import org.braincards.app.util.logError
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Espera tras el ULTIMO cambio local antes de sincronizar (agrupa ediciones seguidas en un solo sync). */
val AUTO_SYNC_DEBOUNCE: Duration = 5.seconds

/** Tope de espera desde el PRIMER cambio pendiente: una racha continua de ediciones sincroniza al menos cada tanto. */
val AUTO_SYNC_MAX_CHANGE_WAIT: Duration = 60.seconds

/** Intervalo minimo entre dos syncs (automaticos o manuales): los disparos que llegan antes se unen en uno. */
val AUTO_SYNC_MIN_INTERVAL: Duration = 30.seconds

/** Sync periodico, SOLO con la app en primer plano. */
val AUTO_SYNC_PERIODIC_INTERVAL: Duration = 5.minutes

/** Tope del ultimo intento al pasar a segundo plano. */
val AUTO_SYNC_BACKGROUND_TIMEOUT: Duration = 10.seconds

/** Backoff tras fallo: el n-esimo fallo seguido espera BASE x 2^(n-1), con tope y jitter. */
val AUTO_SYNC_BACKOFF_BASE: Duration = 30.seconds
val AUTO_SYNC_BACKOFF_MAX: Duration = 15.minutes

/** Fraccion de jitter del backoff: el plazo se multiplica por un factor aleatorio en [1 - f, 1 + f]. */
const val AUTO_SYNC_BACKOFF_JITTER: Double = 0.2

/** Plazo corto para reintentar un disparo que se omitio porque la puerta de pausa estaba cerrada. */
val AUTO_SYNC_PAUSED_RETRY: Duration = 20.seconds

/** Todos los plazos del controlador, con los valores aprobados por defecto (los tests en tiempo real usan otros). */
data class AutoSyncConfig(
    val debounce: Duration = AUTO_SYNC_DEBOUNCE,
    val maxChangeWait: Duration = AUTO_SYNC_MAX_CHANGE_WAIT,
    val minInterval: Duration = AUTO_SYNC_MIN_INTERVAL,
    val periodicInterval: Duration = AUTO_SYNC_PERIODIC_INTERVAL,
    val backgroundTimeout: Duration = AUTO_SYNC_BACKGROUND_TIMEOUT,
    val backoffBase: Duration = AUTO_SYNC_BACKOFF_BASE,
    val backoffMax: Duration = AUTO_SYNC_BACKOFF_MAX,
    val backoffJitter: Double = AUTO_SYNC_BACKOFF_JITTER,
    val pausedRetry: Duration = AUTO_SYNC_PAUSED_RETRY
)

/**
 * Lo unico que el controlador necesita del motor: pedir un sync automatico y ver el resultado del ultimo sync
 * (manual o automatico). Lo implementa SyncCoordinator; el controlador NO tiene ningun otro camino al motor.
 */
interface AutoSyncHost {
    suspend fun requestAutoSync(): AutoSyncOutcome
    val lastOutcome: StateFlow<SyncOutcome?>
}

/**
 * Decide CUANDO sincronizar solo. Un unico bucle (actor) procesa las senales de los monitores y de los flujos
 * (sesion, cambios pendientes, puerta de pausa, resultados) y es el unico que toca el estado: sin carreras.
 *
 * Disparadores: sesion confirmada / abrir la app / volver a primer plano (inmediato); cambio local (debounce con
 * tope de espera); recuperar la conexion (inmediato si habia pendientes o el ultimo intento fallo); apertura de la
 * puerta de pausa; periodico (solo en primer plano); ultimo intento al pasar a segundo plano. Todo sujeto al
 * intervalo minimo y al backoff, salvo el ultimo intento de segundo plano, que se salta debounce e intervalo
 * minimo pero NO el backoff. Pausas: sin sesion confirmada, sin conexion, y la puerta (la comprueba el coordinador
 * dentro del Mutex; el controlador reintenta tras un plazo corto si le dijo "omitido por pausa").
 *
 * Sin bucles: solo un cambio NUEVO (pareja (count, max) distinta y count > 0) cuenta como cambio local; los pushes
 * y pulls propios dejan count == 0 o la misma huella y no disparan nada.
 *
 * Tiempo: [timeSource] monotonico e inyectable (los tests usan el del scheduler virtual); [random] para el jitter.
 */
class AutoSyncController(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val random: Random,
    private val host: AutoSyncHost,
    private val authRepository: AuthRepository,
    private val pendingObserver: PendingChangesObserver,
    private val pauseGate: AutoSyncPauseGate,
    private val foregroundMonitor: ForegroundMonitor,
    private val connectivityMonitor: ConnectivityMonitor,
    private val config: AutoSyncConfig = AutoSyncConfig()
) {

    private sealed interface Signal {
        data class Foreground(val on: Boolean) : Signal
        data class Online(val on: Boolean) : Signal
        data class AuthReady(val ready: Boolean) : Signal
        data class Pending(val stamp: PendingStamp) : Signal
        data class GateOpened(val at: Duration) : Signal
        data class Outcome(val outcome: SyncOutcome) : Signal
    }

    private val signals = Channel<Signal>(Channel.UNLIMITED)
    private var job: Job? = null
    private val origin = timeSource.markNow()

    // --- estado: SOLO lo toca el bucle ---
    private var foreground = false
    private var online = false
    private var authReady = false
    private var lastPending = PendingStamp(0, 0)

    private var immediate = false          // hay que sincronizar en cuanto se pueda
    private var retryAt: Duration? = null  // reintento tras "omitido por pausa"
    private var changeFirstAt: Duration? = null
    private var changeLastAt: Duration? = null
    private var changeDeadline: Duration? = null
    private var periodicAt: Duration? = null
    private var backgroundFinal = false

    private var lastSyncEnd: Duration? = null
    private var backoffUntil: Duration? = null
    private var failures = 0
    private var lastHandledOutcome: SyncOutcome? = null

    // Ultimo intento: cuando empezo, cuando acabo y si llego a sincronizar. Sirve para no duplicar el sync cuando
    // la puerta se abre DURANTE ese intento (la caducidad se detecta al consultar, dentro del propio intento).
    private var attemptStartedAt: Duration = Duration.ZERO
    private var attemptEndedAt: Duration = Duration.ZERO
    private var lastAttemptRan = false

    private fun now(): Duration = origin.elapsedNow()

    /** Arranca los colectores y el bucle. Idempotente. */
    fun start() {
        if (job != null) return
        lastHandledOutcome = host.lastOutcome.value
        job = scope.launch {
            launch { foregroundMonitor.isForeground.collect { signals.trySend(Signal.Foreground(it)) } }
            launch { connectivityMonitor.isOnline.collect { signals.trySend(Signal.Online(it)) } }
            launch {
                authRepository.authState.collect { signals.trySend(Signal.AuthReady(it.isReady())) }
            }
            launch {
                // Solo cambios REALES: otra huella distinta de la anterior; al cambiar de usuario se re-suscribe.
                authRepository.authState
                    .distinctUntilChanged()
                    .flatMapLatest { state ->
                        if (state is AuthState.Authenticated) pendingObserver.observe(state.userId) else emptyFlow()
                    }
                    .distinctUntilChanged()
                    .collect { signals.trySend(Signal.Pending(it)) }
            }
            launch {
                var wasPaused = pauseGate.isPaused.value
                pauseGate.isPaused.collect { paused ->
                    if (wasPaused && !paused) signals.trySend(Signal.GateOpened(now()))
                    wasPaused = paused
                }
            }
            launch { host.lastOutcome.collect { outcome -> if (outcome != null) signals.trySend(Signal.Outcome(outcome)) } }
            runLoop()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun AuthState.isReady() = this is AuthState.Authenticated && sessionConfirmed

    // ------------------------------------------------------------------------------------------------------------
    // Bucle
    // ------------------------------------------------------------------------------------------------------------

    private suspend fun runLoop() {
        while (true) {
            drainSignals()
            val wait = nextWait()
            when {
                wait == null -> apply(signals.receive())
                wait > Duration.ZERO -> withTimeoutOrNull(wait) { signals.receive() }?.let { apply(it) }
                else -> attempt()
            }
        }
    }

    private fun drainSignals() {
        while (true) {
            val signal = signals.tryReceive().getOrNull() ?: return
            apply(signal)
        }
    }

    /** Tiempo hasta el proximo intento (ZERO = ya), o null si no hay nada que hacer / esta en pausa. */
    private fun nextWait(): Duration? {
        if (!authReady || !online) return null
        val now = now()
        if (!foreground) {
            // Segundo plano: solo el ultimo intento. Salta debounce e intervalo minimo, no el backoff.
            if (!backgroundFinal) return null
            return maxOf(Duration.ZERO, (backoffUntil ?: Duration.ZERO) - now)
        }
        val target = listOfNotNull(if (immediate) now else null, changeDeadline, retryAt, periodicAt).minOrNull()
            ?: return null
        val allowedAt = maxOf(
            lastSyncEnd?.let { it + config.minInterval } ?: Duration.ZERO,
            backoffUntil ?: Duration.ZERO
        )
        return maxOf(Duration.ZERO, maxOf(target, allowedAt) - now)
    }

    private suspend fun attempt() {
        val background = !foreground
        attemptStartedAt = now()
        // Se consumen los disparos pendientes: lo que llegue durante el sync cuenta para el siguiente.
        immediate = false
        retryAt = null
        changeFirstAt = null
        changeLastAt = null
        changeDeadline = null
        periodicAt = null
        if (background) backgroundFinal = false

        val outcome: AutoSyncOutcome? = try {
            if (background) {
                withTimeoutOrNull(config.backgroundTimeout) { host.requestAutoSync() }
            } else {
                host.requestAutoSync()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("Sync automatico", e)
            registerFailure()
            null
        }

        when (outcome) {
            is AutoSyncOutcome.Ran -> onOutcome(host.lastOutcome.value)
            is AutoSyncOutcome.Skipped -> when (outcome.reason) {
                AutoSyncSkipReason.PAUSED_SIGN_OUT, AutoSyncSkipReason.PAUSED_STUDY ->
                    if (foreground) retryAt = now() + config.pausedRetry
                // Sin sesion / sin confirmar: lo reactivara el flujo de sesion.
                AutoSyncSkipReason.NO_SESSION, AutoSyncSkipReason.SESSION_UNCONFIRMED -> Unit
            }
            null -> Unit
        }
        attemptEndedAt = now()
        lastAttemptRan = outcome is AutoSyncOutcome.Ran
        if (foreground && periodicAt == null) periodicAt = now() + config.periodicInterval
    }

    // ------------------------------------------------------------------------------------------------------------
    // Senales
    // ------------------------------------------------------------------------------------------------------------

    private fun apply(signal: Signal) {
        when (signal) {
            is Signal.Foreground -> {
                val was = foreground
                foreground = signal.on
                if (signal.on && !was) {
                    immediate = true
                    backgroundFinal = false
                    periodicAt = now() + config.periodicInterval
                } else if (!signal.on && was) {
                    backgroundFinal = authReady && (lastPending.count > 0 || changeDeadline != null)
                }
            }
            is Signal.Online -> {
                val was = online
                online = signal.on
                if (signal.on && !was && (lastPending.count > 0 || failures > 0)) immediate = true
            }
            is Signal.AuthReady -> {
                val was = authReady
                authReady = signal.ready
                if (signal.ready && !was) {
                    immediate = true
                } else if (!signal.ready) {
                    immediate = false
                    retryAt = null
                    backgroundFinal = false
                    periodicAt = null
                    clearChangeWindow()
                    failures = 0
                    backoffUntil = null
                    lastSyncEnd = null
                }
            }
            is Signal.Pending -> {
                lastPending = signal.stamp
                if (signal.stamp.count > 0) {
                    val now = now()
                    val first = changeFirstAt ?: now
                    changeFirstAt = first
                    changeLastAt = now
                    changeDeadline = minOf(now + config.debounce, first + config.maxChangeWait)
                } else {
                    clearChangeWindow()
                }
            }
            is Signal.GateOpened -> {
                val openedDuringRunningAttempt =
                    lastAttemptRan && signal.at >= attemptStartedAt && signal.at <= attemptEndedAt
                if (!openedDuringRunningAttempt) immediate = true
            }
            is Signal.Outcome -> onOutcome(signal.outcome)
        }
    }

    private fun clearChangeWindow() {
        changeFirstAt = null
        changeLastAt = null
        changeDeadline = null
    }

    /** Idempotente por identidad: lo llama el bucle (sync propio) y el colector de lastOutcome (sync manual). */
    private fun onOutcome(outcome: SyncOutcome?) {
        if (outcome == null || outcome === lastHandledOutcome) return
        lastHandledOutcome = outcome
        val result = outcome.result
        if (result is SyncResult.NotAuthenticated) return
        val now = now()
        lastSyncEnd = now
        periodicAt = if (foreground) now + config.periodicInterval else null
        val success = result is SyncResult.Completed && result.isFullySuccessful()
        if (success) {
            failures = 0
            backoffUntil = null
        } else {
            registerFailure()
        }
    }

    private fun registerFailure() {
        failures++
        backoffUntil = now() + backoffDelay(failures)
        immediate = true // reintento propio, al ritmo del backoff
    }

    /** BASE x 2^(n-1), con jitter en [1-f, 1+f] y tope final. */
    private fun backoffDelay(consecutiveFailures: Int): Duration {
        var delay = config.backoffBase
        repeat(consecutiveFailures - 1) {
            delay = if (delay >= config.backoffMax) config.backoffMax else delay * 2
        }
        val factor = 1.0 + (random.nextDouble() * 2.0 - 1.0) * config.backoffJitter
        return minOf(delay * factor, config.backoffMax)
    }
}
