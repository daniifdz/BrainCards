package org.braincards.app.presentation.sync

import androidx.datastore.core.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Interruptor de sincronizacion automatica en el AutoSyncController (tiempo virtual). Apagado no dispara NADA
 * automatico; desconocido (antes del primer valor) cuenta como apagado; encendido sincroniza de inmediato y empieza
 * de cero con fallos y backoff.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoSyncControllerEnabledTest {

    private val neutralRandom = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = 0.5
    }

    private val okTable = PullTableResult(applied = 0, aborted = false)
    private val abortedTable = PullTableResult(applied = 0, aborted = true, failure = PullFailure.NETWORK)
    private val pushOk = PushTableResult(succeeded = 0, failed = 0)
    private fun success() = SyncResult.Completed(PullResult(okTable, okTable, okTable, okTable), PushResult(pushOk, pushOk, pushOk, pushOk))
    private fun failure() = SyncResult.Completed(PullResult(abortedTable, okTable, okTable, okTable), PushResult(pushOk, pushOk, pushOk, pushOk))

    private class FakeForeground(initial: Boolean) : ForegroundMonitor {
        val state = MutableStateFlow(initial)
        override val isForeground: StateFlow<Boolean> = state.asStateFlow()
    }

    private class FakeConnectivity(initial: Boolean) : ConnectivityMonitor {
        val state = MutableStateFlow(initial)
        override val isOnline: StateFlow<Boolean> = state.asStateFlow()
    }

    private class FakePending : PendingChangesObserver {
        val stamp = MutableStateFlow(PendingStamp(0, 0))
        override fun observe(userId: String) = stamp
    }

    private class FakeHost(private val test: TestScope) : AutoSyncHost {
        val calls = mutableListOf<Long>()
        var ran = 0
        var script: (Int) -> SyncResult = { SyncResult.Completed(
            PullResult(PullTableResult(0, false), PullTableResult(0, false), PullTableResult(0, false), PullTableResult(0, false)),
            PushResult(PushTableResult(0, 0), PushTableResult(0, 0), PushTableResult(0, 0), PushTableResult(0, 0))
        ) }
        var skipAsDisabled = false
        override val lastOutcome = MutableStateFlow<SyncOutcome?>(null)

        override suspend fun requestAutoSync(): AutoSyncOutcome {
            calls += test.currentTime
            if (skipAsDisabled) return AutoSyncOutcome.Skipped(AutoSyncSkipReason.DISABLED)
            val result = script(ran++)
            lastOutcome.value = SyncOutcome(result, Instant.fromEpochMilliseconds(test.currentTime))
            return AutoSyncOutcome.Ran(result)
        }
    }

    private val confirmed = AuthState.Authenticated("user-1", "a@b.c", sessionConfirmed = true)

    private inner class Env(
        val test: TestScope,
        val enabled: Flow<Boolean>,
        foregroundOn: Boolean = true,
        online: Boolean = true
    ) {
        val foreground = FakeForeground(foregroundOn)
        val connectivity = FakeConnectivity(online)
        val pending = FakePending()
        val host = FakeHost(test)
        val controller = AutoSyncController(
            scope = test.backgroundScope,
            timeSource = test.testTimeSource,
            random = neutralRandom,
            host = host,
            authRepository = FakeAuthRepository(confirmed),
            pendingObserver = pending,
            pauseGate = AutoSyncPauseGate(timeSource = test.testTimeSource),
            foregroundMonitor = foreground,
            connectivityMonitor = connectivity,
            autoSyncEnabled = enabled
        )

        fun start() {
            controller.start()
            test.runCurrent()
        }

        fun advance(d: Duration) {
            test.advanceTimeBy(d)
            test.runCurrent()
        }

        fun change(count: Int, max: Long) {
            pending.stamp.value = PendingStamp(count, max)
            test.runCurrent()
        }
    }

    private fun Number.s(): Long = toLong() * 1000L

    // --- apagado: no dispara nada automatico -----------------------------------------------------------------------------

    @Test
    fun apagado_desde_el_arranque_no_sincroniza_aunque_haya_sesion_red_y_primer_plano() = runTest {
        val env = Env(this, MutableStateFlow(false))

        env.start()
        env.advance(30.minutes) // incluye seis periodos de 5 min

        assertTrue(env.host.calls.isEmpty(), "ninguna peticion: ${env.host.calls}")
    }

    @Test
    fun apagado_un_cambio_local_no_dispara_el_sync() = runTest {
        val env = Env(this, MutableStateFlow(false))
        env.start()

        env.change(3, 100)
        env.advance(2.minutes)

        assertTrue(env.host.calls.isEmpty())
    }

    @Test
    fun apagado_recuperar_la_red_con_pendientes_no_dispara_el_sync() = runTest {
        val env = Env(this, MutableStateFlow(false), online = false)
        env.start()
        env.change(2, 100)

        env.connectivity.state.value = true
        env.advance(2.minutes)

        assertTrue(env.host.calls.isEmpty())
    }

    @Test
    fun apagado_volver_a_primer_plano_no_dispara_el_sync() = runTest {
        val env = Env(this, MutableStateFlow(false), foregroundOn = false)
        env.start()

        env.foreground.state.value = true
        env.advance(2.minutes)

        assertTrue(env.host.calls.isEmpty())
    }

    @Test
    fun apagado_pasar_a_segundo_plano_con_pendientes_no_hace_el_ultimo_intento() = runTest {
        val env = Env(this, MutableStateFlow(false))
        env.start()
        env.change(2, 100)

        env.foreground.state.value = false
        env.advance(1.minutes)

        assertTrue(env.host.calls.isEmpty())
    }

    // --- desconocido = apagado ---------------------------------------------------------------------------------------

    @Test
    fun hasta_que_llega_el_primer_valor_no_sincroniza_y_con_el_valor_encendido_si() = runTest {
        val flow = MutableSharedFlow<Boolean>() // DataStore aun no ha respondido
        val env = Env(this, flow)
        env.start()

        env.advance(1.minutes)
        assertTrue(env.host.calls.isEmpty(), "desconocido cuenta como apagado")

        flow.emit(true)
        runCurrent()
        assertEquals(listOf(60.s()), env.host.calls)
    }

    @Test
    fun si_el_primer_valor_es_apagado_nunca_llega_a_sincronizar() = runTest {
        val flow = MutableSharedFlow<Boolean>()
        val env = Env(this, flow)
        env.start()

        flow.emit(false)
        env.advance(10.minutes)

        assertTrue(env.host.calls.isEmpty())
    }

    // --- apagar y encender en marcha ---------------------------------------------------------------------------------

    @Test
    fun apagar_en_marcha_deja_quieto_el_controlador_aunque_hubiera_un_cambio_armado() = runTest {
        val enabled = MutableStateFlow(true)
        val env = Env(this, enabled)
        env.start()
        assertEquals(listOf(0L), env.host.calls)
        env.advance(40.seconds)
        env.change(1, 100) // debounce de 5 s armado

        enabled.value = false
        env.advance(10.minutes)

        assertEquals(listOf(0L), env.host.calls, "ni el cambio armado ni el periodico")
    }

    @Test
    fun encender_sincroniza_de_inmediato_respetando_el_intervalo_minimo() = runTest {
        val enabled = MutableStateFlow(false)
        val env = Env(this, enabled)
        env.start()
        env.advance(100.seconds)
        assertTrue(env.host.calls.isEmpty())

        enabled.value = true
        env.test.runCurrent()

        assertEquals(listOf(100.s()), env.host.calls)
    }

    @Test
    fun encender_tras_haber_sincronizado_ya_en_marcha_tambien_sincroniza_de_inmediato() = runTest {
        // Aqui no queda ningun disparo armado de antes: el sync inmediato lo pide el propio hecho de encender.
        val enabled = MutableStateFlow(true)
        val env = Env(this, enabled)
        env.start()
        assertEquals(listOf(0L), env.host.calls)
        env.advance(100.seconds)
        enabled.value = false
        env.advance(10.seconds)

        enabled.value = true
        env.test.runCurrent()

        assertEquals(listOf(0L, 110.s()), env.host.calls)
    }

    @Test
    fun encender_reinicia_los_fallos_y_el_backoff() = runTest {
        val enabled = MutableStateFlow(true)
        val env = Env(this, enabled)
        env.host.script = { failure() } // todos fallan: backoff 30 s y luego 60 s
        env.start()                      // fallo a t=0 -> reintento a 30 s
        env.advance(30.seconds)          // fallo a t=30 -> el backoff pasa a 60 s (hasta t=90)
        assertEquals(listOf(0L, 30.s()), env.host.calls)
        env.advance(10.seconds)

        enabled.value = false
        env.advance(10.seconds)
        enabled.value = true // t=50: sin reiniciar el backoff esperaria hasta t=90
        env.test.runCurrent()
        env.advance(10.seconds)

        assertEquals(listOf(0L, 30.s(), 60.s()), env.host.calls, "intervalo minimo (30 s tras el ultimo sync), no el backoff de 60 s")
    }

    @Test
    fun encender_estando_en_segundo_plano_no_provoca_un_ultimo_intento() = runTest {
        val enabled = MutableStateFlow(false)
        val env = Env(this, enabled)
        env.start()
        env.change(2, 100)
        env.foreground.state.value = false // segundo plano con pendientes, pero apagado: no se arma el ultimo intento
        env.test.runCurrent()

        enabled.value = true
        env.advance(1.minutes)

        assertTrue(env.host.calls.isEmpty())
    }

    // --- un fallo al leer el interruptor no mata el controlador ------------------------------------------------------------

    @Test
    fun si_leer_el_interruptor_lanza_IOException_el_controlador_sigue_vivo_y_asume_activado() = runTest {
        val env = Env(this, flow { throw IOException("preferencias corruptas") })

        env.start()
        assertEquals(listOf(0L), env.host.calls, "asume activado: sincroniza al arrancar")
        env.advance(40.seconds)
        env.change(1, 100)
        env.advance(10.seconds)

        assertEquals(listOf(0L, 45.s()), env.host.calls, "y sigue reaccionando a los cambios: el bucle no murio")
    }

    // --- el coordinador dice "omitido por apagado" ----------------------------------------------------------------------

    @Test
    fun un_omitido_por_apagado_no_provoca_reintentos_en_bucle() = runTest {
        val env = Env(this, MutableStateFlow(true)) // el controlador aun lo ve encendido
        env.host.skipAsDisabled = true

        env.start()
        env.advance(2.minutes)

        assertEquals(listOf(0L), env.host.calls, "una sola peticion, sin reintento ni backoff")
    }
}
