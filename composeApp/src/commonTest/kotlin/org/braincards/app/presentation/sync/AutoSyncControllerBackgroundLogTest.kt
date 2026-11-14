package org.braincards.app.presentation.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.PendingStamp
import org.braincards.app.domain.repository.PendingChangesObserver
import org.braincards.app.domain.usecase.Sync.PullFailure
import org.braincards.app.domain.usecase.Sync.PullResult
import org.braincards.app.domain.usecase.Sync.PullTableResult
import org.braincards.app.domain.usecase.Sync.PushResult
import org.braincards.app.domain.usecase.Sync.PushTableResult
import org.braincards.app.domain.usecase.Sync.SyncResult
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Trazas de diagnostico del ultimo intento en segundo plano: que se cuenta en cada caso (inicio, fin con duracion y
 * tipo de resultado, timeout, motivo de omision) y que NUNCA aparece nada sensible. El comportamiento del controlador
 * lo cubre AutoSyncControllerTest; aqui solo se mira el texto de las trazas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoSyncControllerBackgroundLogTest {

    private val neutralRandom = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = 0.5
    }

    private val ok = PullTableResult(applied = 0, aborted = false)
    private val aborted = PullTableResult(applied = 0, aborted = true, failure = PullFailure.NETWORK)
    private val pushOk = PushTableResult(succeeded = 0, failed = 0)
    private fun success() = SyncResult.Completed(PullResult(ok, ok, ok, ok), PushResult(pushOk, pushOk, pushOk, pushOk))
    private fun failure() = SyncResult.Completed(PullResult(aborted, ok, ok, ok), PushResult(pushOk, pushOk, pushOk, pushOk))

    private class Monitor(initial: Boolean) : ForegroundMonitor, ConnectivityMonitor {
        val state = MutableStateFlow(initial)
        override val isForeground: StateFlow<Boolean> = state.asStateFlow()
        override val isOnline: StateFlow<Boolean> = state.asStateFlow()
    }

    private class FakePending : PendingChangesObserver {
        val stamp = MutableStateFlow(PendingStamp(0, 0))
        override fun observe(userId: String) = stamp
    }

    private class Host(private val test: TestScope, private val gate: AutoSyncPauseGate) : AutoSyncHost {
        var script: () -> SyncResult = { SyncResult.Completed(
            PullResult(PullTableResult(0, false), PullTableResult(0, false), PullTableResult(0, false), PullTableResult(0, false)),
            PushResult(PushTableResult(0, 0), PushTableResult(0, 0), PushTableResult(0, 0), PushTableResult(0, 0))
        ) }
        var throwSecret = false
        var hangMillis = 0L
        override val lastOutcome = MutableStateFlow<SyncOutcome?>(null)

        override suspend fun requestAutoSync(): AutoSyncOutcome {
            when (gate.activeReason()) {
                PauseReason.SIGN_OUT -> return AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_SIGN_OUT)
                PauseReason.STUDY -> return AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_STUDY)
                null -> Unit
            }
            if (throwSecret) throw IllegalStateException("Bearer secret-token-123 card: pregunta privada")
            try {
                if (hangMillis > 0) delay(hangMillis)
            } catch (e: CancellationException) {
                throw e
            }
            val result = script()
            lastOutcome.value = SyncOutcome(result, Instant.fromEpochMilliseconds(0))
            return AutoSyncOutcome.Ran(result)
        }
    }

    private class Env(val test: TestScope, auth: AuthState, online: Boolean) {
        val logs = mutableListOf<String>()
        val foreground = Monitor(true)
        val connectivity = Monitor(online)
        val pending = FakePending()
        val gate = AutoSyncPauseGate(timeSource = test.testTimeSource)
        val host = Host(test, gate)
        val controller = AutoSyncController(
            scope = test.backgroundScope,
            timeSource = test.testTimeSource,
            random = object : Random() {
                override fun nextBits(bitCount: Int): Int = 0
                override fun nextDouble(): Double = 0.5
            },
            host = host,
            authRepository = FakeAuthRepository(auth),
            pendingObserver = pending,
            pauseGate = gate,
            foregroundMonitor = foreground,
            connectivityMonitor = connectivity,
            log = { logs += it }
        )

        fun start() { controller.start(); test.runCurrent() }
        fun advance(d: Duration) { test.advanceTimeBy(d); test.runCurrent() }
        fun change(count: Int) { pending.stamp.value = PendingStamp(count, 100); test.runCurrent() }
        fun goBackground() { foreground.state.value = false; test.runCurrent() }
    }

    private val confirmed = AuthState.Authenticated("user-1", "a@b.c", sessionConfirmed = true)

    private fun TestScope.env(auth: AuthState = confirmed, online: Boolean = true) = Env(this, auth, online)

    @Test
    fun con_pendientes_arma_el_ultimo_intento_y_cuenta_inicio_y_fin_con_duracion_y_resultado() = runTest {
        val env = env()
        env.start()
        env.advance(40.seconds)
        env.change(2)
        env.logs.clear()

        env.goBackground()

        assertEquals(
            listOf(
                "segundo plano: ultimo intento armado pendientes=2",
                "ultimo intento en segundo plano: inicio",
                "ultimo intento en segundo plano: fin duracionMs=0 resultado=COMPLETED_OK"
            ),
            env.logs
        )
    }

    @Test
    fun un_resultado_con_fallos_se_etiqueta_aparte() = runTest {
        val env = env()
        env.start()
        env.advance(40.seconds)
        env.change(1)
        env.host.script = { failure() }
        env.logs.clear()

        env.goBackground()

        assertTrue(env.logs.last().endsWith("resultado=COMPLETED_WITH_FAILURES"), env.logs.toString())
    }

    @Test
    fun sin_pendientes_se_omite_con_motivo_NO_PENDING_y_no_hay_inicio() = runTest {
        val env = env()
        env.start()
        env.advance(40.seconds)
        env.logs.clear()

        env.goBackground()

        assertEquals(listOf("segundo plano: ultimo intento omitido motivo=NO_PENDING"), env.logs)
    }

    @Test
    fun sin_sesion_se_omite_con_motivo_NOT_AUTHENTICATED() = runTest {
        val env = env(auth = AuthState.NotAuthenticated)
        env.start()
        env.change(1)
        env.logs.clear()

        env.goBackground()

        assertEquals(listOf("segundo plano: ultimo intento omitido motivo=NOT_AUTHENTICATED"), env.logs)
    }

    @Test
    fun sin_conexion_se_aplaza_con_motivo_OFFLINE() = runTest {
        val env = env()
        env.start()
        env.advance(40.seconds)
        env.change(3)
        env.connectivity.state.value = false
        env.test.runCurrent()
        env.logs.clear()

        env.goBackground()

        assertEquals(listOf("segundo plano: ultimo intento aplazado motivo=OFFLINE pendientes=3"), env.logs)
    }

    @Test
    fun en_backoff_se_aplaza_con_motivo_BACKOFF_y_la_espera_en_ms() = runTest {
        val env = env()
        env.host.script = { failure() }
        env.start()                 // el sync de arranque falla: backoff de 30 s
        env.advance(5.seconds)
        env.change(1)
        env.logs.clear()

        env.goBackground()

        assertEquals("segundo plano: ultimo intento aplazado motivo=BACKOFF pendientes=1 esperaMs=25000", env.logs.first())
    }

    @Test
    fun con_la_puerta_de_estudio_cerrada_el_fin_cuenta_PAUSED() = runTest {
        val env = env()
        env.start()
        env.advance(40.seconds)
        env.change(1)
        env.gate.acquire(PauseReason.STUDY)
        env.logs.clear()

        env.goBackground()

        assertEquals(
            "ultimo intento en segundo plano: fin duracionMs=0 resultado=SKIPPED_PAUSED (PAUSED_STUDY)",
            env.logs.last()
        )
    }

    @Test
    fun el_timeout_de_10_s_se_traza_con_su_limite_y_la_duracion() = runTest {
        val env = env()
        env.start()
        env.advance(40.seconds)
        env.change(1)
        env.host.hangMillis = 60_000
        env.logs.clear()

        env.goBackground()
        env.advance(10.seconds)

        assertTrue("segundo plano: ultimo intento TIMEOUT limiteMs=10000" in env.logs, env.logs.toString())
        assertEquals("ultimo intento en segundo plano: fin duracionMs=10000 resultado=TIMEOUT", env.logs.last())
    }

    @Test
    fun una_excepcion_se_traza_como_EXCEPTION_sin_su_mensaje() = runTest {
        val env = env()
        env.start()
        env.advance(40.seconds)
        env.change(1)
        env.host.throwSecret = true
        env.logs.clear()

        env.goBackground()

        assertEquals("ultimo intento en segundo plano: fin duracionMs=0 resultado=EXCEPTION", env.logs.last())
        env.logs.forEach {
            assertTrue("secret" !in it && "pregunta" !in it && "Bearer" !in it, "traza con contenido sensible: $it")
        }
    }

    @Test
    fun el_primer_plano_no_genera_trazas_de_segundo_plano() = runTest {
        val env = env()
        env.start()
        env.advance(40.seconds)
        env.change(1)
        env.advance(10.seconds)

        assertTrue(env.logs.isEmpty(), env.logs.toString())
    }
}
