package org.braincards.app.presentation.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * AutoSyncController con tiempo virtual: un host falso que registra cuando se le pide un sync, monitores falsos y
 * un observador de pendientes falso. Los tiempos esperados salen de los plazos aprobados (5 s / 60 s / 30 s / 5 min
 * / 10 s / 30 s x 2^n con tope de 15 min); el jitter es neutro salvo en los tests de jitter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoSyncControllerTest {

    // --- fakes ---------------------------------------------------------------------------------------------------------

    private val neutralRandom = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = 0.5 // factor de jitter 1.0
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

    /** Host que registra el instante virtual (ms) de cada peticion y responde segun un guion. */
    private class FakeHost(private val test: TestScope, private val gate: AutoSyncPauseGate?) : AutoSyncHost {
        val calls = mutableListOf<Long>()
        var ran = 0
        var cancelled = 0
        var hangMillis = 0L
        var script: (Int) -> SyncResult = { SyncResult.Completed(
            PullResult(PullTableResult(0, false), PullTableResult(0, false), PullTableResult(0, false), PullTableResult(0, false)),
            PushResult(PushTableResult(0, 0), PushTableResult(0, 0), PushTableResult(0, 0), PushTableResult(0, 0))
        ) }
        override val lastOutcome = MutableStateFlow<SyncOutcome?>(null)

        override suspend fun requestAutoSync(): AutoSyncOutcome {
            calls += test.currentTime
            when (gate?.activeReason()) {
                PauseReason.SIGN_OUT -> return AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_SIGN_OUT)
                PauseReason.STUDY -> return AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_STUDY)
                null -> Unit
            }
            try {
                if (hangMillis > 0) delay(hangMillis)
            } catch (e: CancellationException) {
                cancelled++
                throw e
            }
            val result = script(ran++)
            lastOutcome.value = SyncOutcome(result, Instant.fromEpochMilliseconds(test.currentTime))
            return AutoSyncOutcome.Ran(result)
        }

        /** Un sync MANUAL: deja su resultado en lastOutcome sin pasar por requestAutoSync. */
        fun manualSync(result: SyncResult) {
            lastOutcome.value = SyncOutcome(result, Instant.fromEpochMilliseconds(test.currentTime))
        }
    }

    private val confirmed = AuthState.Authenticated("user-1", "a@b.c", sessionConfirmed = true)

    private inner class Env(
        val test: TestScope,
        authState: AuthState = confirmed,
        foregroundOn: Boolean = true,
        online: Boolean = true,
        config: AutoSyncConfig = AutoSyncConfig(),
        random: Random = neutralRandom,
        withGate: Boolean = false
    ) {
        val foreground = FakeForeground(foregroundOn)
        val connectivity = FakeConnectivity(online)
        val auth = FakeAuthRepository(authState)
        val pending = FakePending()
        val gate = AutoSyncPauseGate(timeSource = test.testTimeSource)
        val host = FakeHost(test, if (withGate) gate else null)
        val controller = AutoSyncController(
            scope = test.backgroundScope,
            timeSource = test.testTimeSource,
            random = random,
            host = host,
            authRepository = auth,
            pendingObserver = pending,
            pauseGate = gate,
            foregroundMonitor = foreground,
            connectivityMonitor = connectivity,
            config = config
        )

        fun start() {
            controller.start()
            test.runCurrent()
        }

        /** Avanza el tiempo virtual [d] ejecutando todo lo que venza justo en ese instante. */
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

    // --- abrir la app / periodico --------------------------------------------------------------------------------------

    @Test
    fun al_abrir_la_app_con_sesion_confirmada_sincroniza_una_vez_de_inmediato() = runTest {
        val env = Env(this)

        env.start()

        assertEquals(listOf(0L), env.host.calls)
    }

    @Test
    fun el_periodico_sincroniza_cada_5_minutos_en_primer_plano() = runTest {
        val env = Env(this)
        env.start()

        env.advance(5.minutes - 1.seconds)
        assertEquals(1, env.host.calls.size)
        env.advance(1.seconds)
        assertEquals(listOf(0L, 300.s()), env.host.calls)

        env.advance(10.minutes)
        assertEquals(listOf(0L, 300.s(), 600.s(), 900.s()), env.host.calls)
    }

    // --- debounce ------------------------------------------------------------------------------------------------------

    @Test
    fun varios_cambios_seguidos_dan_un_solo_sync_5_s_despues_del_ultimo() = runTest {
        val env = Env(this)
        env.start()
        env.advance(40.seconds)

        env.change(1, 100)
        env.advance(2.seconds)
        env.change(1, 200)
        env.advance(2.seconds)
        env.change(1, 300)
        env.advance(4.seconds)
        assertEquals(1, env.host.calls.size, "aun dentro del debounce")
        env.advance(1.seconds)

        assertEquals(listOf(0L, 49.s()), env.host.calls)
    }

    @Test
    fun una_racha_larga_de_cambios_sincroniza_cada_60_s_por_el_tope_de_espera() = runTest {
        val env = Env(this)
        env.start()
        env.advance(41.seconds)

        // Un cambio nuevo cada 3,5 s (siempre antes de que venza el debounce de 5 s): sin tope no sincronizaria nunca.
        var max = 100L
        repeat(57) {
            env.change(1, max++)
            env.advance(3500.milliseconds)
        }

        // Primer cambio a los 41 s -> tope a los 101 s; luego 104 -> 164; 167 -> 227 (un sync cada 60 s).
        assertEquals(listOf(0L, 101.s(), 164.s(), 227.s()), env.host.calls)
    }

    // --- intervalo minimo ----------------------------------------------------------------------------------------------

    @Test
    fun el_intervalo_minimo_retrasa_el_sync_y_une_los_disparos() = runTest {
        val env = Env(this)
        env.start() // sync a t=0
        env.advance(2.seconds)

        env.change(1, 100)            // debounce -> 7 s, pero el intervalo minimo manda: 30 s
        env.advance(8.seconds)
        env.connectivity.state.value = false
        env.advance(1.seconds)
        env.connectivity.state.value = true // reconectar con pendientes: otro disparo inmediato
        env.advance(1.seconds)
        assertEquals(1, env.host.calls.size, "a los 12 s sigue sin sincronizar")
        env.advance(18.seconds)

        assertEquals(listOf(0L, 30.s()), env.host.calls, "todos los disparos unidos en un solo sync")
    }

    // --- backoff -------------------------------------------------------------------------------------------------------

    @Test
    fun el_backoff_crece_x2_hasta_el_tope_de_15_minutos() = runTest {
        val env = Env(this)
        env.host.script = { failure() }
        env.start()

        env.advance(2731.seconds)

        // 30, 60, 120, 240, 480, 960 -> tope 900, 900
        assertEquals(listOf(0L, 30.s(), 90.s(), 210.s(), 450.s(), 930.s(), 1830.s(), 2730.s()), env.host.calls)
    }

    @Test
    fun con_backoff_activo_ni_la_vuelta_a_primer_plano_ni_el_debounce_saltan_la_espera() = runTest {
        val env = Env(this)
        env.host.script = { failure() }
        env.start() // fallo a t=0 -> backoff hasta 30 s
        env.advance(3.seconds)
        env.change(1, 100)                       // debounce vencería a los 8 s
        env.advance(2.seconds)
        env.foreground.state.value = false       // 5 s: segundo plano con pendientes (ultimo intento)...
        env.advance(1.seconds)
        env.foreground.state.value = true        // ...y vuelta a primer plano
        env.advance(10.seconds)

        assertEquals(1, env.host.calls.size, "a los 16 s sigue en backoff")
        env.advance(14.seconds)
        assertEquals(listOf(0L, 30.s()), env.host.calls)
    }

    @Test
    fun el_jitter_es_determinista_con_la_misma_semilla_y_queda_dentro_de_su_margen() = runTest {
        fun gaps(seed: Int): List<Long> {
            val env = Env(this, random = Random(seed))
            env.host.script = { failure() }
            env.start()
            env.advance(1000.seconds)
            val calls = env.host.calls.toList()
            env.controller.stop()
            return calls.zipWithNext { a, b -> b - a }
        }

        val first = gaps(1234)
        val second = gaps(1234)

        assertEquals(first, second, "misma semilla, mismos plazos")
        // plazos nominales 30, 60, 120, 240 s con +-20 %
        listOf(30L, 60L, 120L, 240L).forEachIndexed { i, nominal ->
            val gap = first[i]
            assertTrue(gap in (nominal.s() * 8 / 10)..(nominal.s() * 12 / 10), "hueco $i = $gap ms, nominal ${nominal.s()}")
        }
    }

    @Test
    fun un_exito_reinicia_el_backoff() = runTest {
        val env = Env(this)
        env.host.script = { n -> if (n == 0 || n == 1 || n == 3) failure() else success() }
        env.start()

        env.advance(421.seconds)

        // fallo (0) -> 30 s fallo (30) -> exito (90) -> periodico a 390: fallo -> reintento 30 s despues (no 60 ni 120)
        assertEquals(listOf(0L, 30.s(), 90.s(), 390.s(), 420.s()), env.host.calls)
    }

    @Test
    fun un_sync_manual_correcto_reinicia_el_backoff_y_el_intervalo() = runTest {
        val env = Env(this)
        env.host.script = { failure() }
        env.start()                       // fallo a 0 (backoff 30 s)
        env.advance(30.seconds)           // fallo a 30 s (backoff hasta 90 s)
        assertEquals(listOf(0L, 30.s()), env.host.calls)

        env.advance(10.seconds)           // 40 s: el usuario pulsa "Sincronizar ahora" y va bien
        env.host.manualSync(success())
        env.test.runCurrent()
        env.advance(29.seconds)
        assertEquals(2, env.host.calls.size, "hasta 40 + 30 = 70 s no vuelve a sincronizar solo")
        env.advance(1.seconds)

        assertEquals(listOf(0L, 30.s(), 70.s()), env.host.calls, "ya no espera al backoff antiguo (90 s)")
    }

    // --- pausas --------------------------------------------------------------------------------------------------------

    @Test
    fun sin_sesion_no_sincroniza_y_al_iniciarla_si() = runTest {
        val env = Env(this, authState = AuthState.NotAuthenticated)
        env.start()
        env.change(1, 100)
        env.advance(10.minutes)
        assertEquals(emptyList(), env.host.calls)

        env.auth.setState(confirmed)
        env.test.runCurrent()

        assertEquals(listOf(10.minutes.inWholeMilliseconds), env.host.calls)
    }

    @Test
    fun con_la_sesion_sin_confirmar_no_sincroniza_hasta_confirmarla() = runTest {
        val env = Env(this, authState = AuthState.Authenticated("user-1", "a@b.c", sessionConfirmed = false))
        env.start()
        env.advance(10.minutes)
        assertEquals(emptyList(), env.host.calls)

        env.auth.setState(confirmed)
        env.test.runCurrent()

        assertEquals(1, env.host.calls.size)
    }

    @Test
    fun sin_conexion_no_sincroniza_y_al_volver_la_red_con_pendientes_si_de_inmediato() = runTest {
        val env = Env(this, online = false)
        env.start()
        env.change(1, 100)
        env.advance(10.minutes)
        assertEquals(emptyList(), env.host.calls)

        env.connectivity.state.value = true
        env.test.runCurrent()

        assertEquals(listOf(10.minutes.inWholeMilliseconds), env.host.calls)
    }

    @Test
    fun al_volver_la_red_sin_pendientes_ni_fallos_no_hace_nada() = runTest {
        val env = Env(this)
        env.start()
        env.advance(10.seconds)

        env.connectivity.state.value = false
        env.advance(10.seconds)
        env.connectivity.state.value = true
        env.advance(10.seconds)

        assertEquals(listOf(0L), env.host.calls)
    }

    @Test
    fun al_volver_la_red_tras_un_fallo_reintenta_al_acabar_el_backoff() = runTest {
        val env = Env(this)
        env.host.script = { n -> if (n == 0) failure() else success() }
        env.start()                       // fallo a 0, backoff hasta 30 s
        env.advance(5.seconds)
        env.connectivity.state.value = false
        env.advance(15.seconds)
        env.connectivity.state.value = true // a los 20 s
        env.advance(9.seconds)
        assertEquals(1, env.host.calls.size)
        env.advance(1.seconds)

        assertEquals(listOf(0L, 30.s()), env.host.calls)
    }

    @Test
    fun al_volver_la_red_se_reinicia_el_backoff_pero_se_mantiene_el_intervalo_minimo() = runTest {
        val env = Env(this)
        env.host.script = { n -> if (n < 3) failure() else success() }
        env.start()                       // fallos a 0 y 30; el tercero a 90 s deja el backoff hasta 210 s
        env.advance(90.seconds)
        assertEquals(listOf(0L, 30.s(), 90.s()), env.host.calls)

        env.advance(10.seconds)           // 100 s
        env.connectivity.state.value = false
        env.advance(10.seconds)
        env.connectivity.state.value = true // 110 s: red restaurada
        env.advance(9.seconds)
        assertEquals(3, env.host.calls.size, "a los 119 s sigue el intervalo minimo (90 + 30 = 120 s)")
        env.advance(1.seconds)

        assertEquals(listOf(0L, 30.s(), 90.s(), 120.s()), env.host.calls, "sin esperar al backoff antiguo (210 s)")
    }

    @Test
    fun tras_reiniciar_el_backoff_por_la_red_el_siguiente_fallo_vuelve_a_esperar_30_s() = runTest {
        val env = Env(this)
        env.host.script = { failure() }
        env.start()                       // fallos a 0, 30 (backoff 60 s: hasta 90)
        env.advance(30.seconds)
        env.connectivity.state.value = false
        env.advance(5.seconds)
        env.connectivity.state.value = true // 35 s: reinicio; el siguiente intento a 60 s (30 + intervalo minimo)
        env.advance(25.seconds)
        assertEquals(listOf(0L, 30.s(), 60.s()), env.host.calls)

        env.advance(30.seconds) // el fallo de 60 s es el primero tras el reinicio: espera 30 s, no 120

        assertEquals(listOf(0L, 30.s(), 60.s(), 90.s()), env.host.calls)
    }

    // --- puerta de pausa -----------------------------------------------------------------------------------------------

    @Test
    fun si_la_puerta_esta_cerrada_reintenta_cada_20_s_y_al_abrirse_lanza_un_solo_sync() = runTest {
        val env = Env(this, withGate = true)
        val lease = env.gate.acquire(PauseReason.STUDY)
        env.start()

        env.advance(61.seconds)
        assertEquals(listOf(0L, 20.s(), 40.s(), 60.s()), env.host.calls)
        assertEquals(0, env.host.ran)

        env.advance(9.seconds) // 70 s: se libera la puerta
        lease.release()
        env.test.runCurrent()
        env.advance(60.seconds)

        assertEquals(1, env.host.ran, "un solo sync tras abrirse la puerta")
        assertEquals(listOf(0L, 20.s(), 40.s(), 60.s(), 70.s()), env.host.calls)
    }

    @Test
    fun con_dos_leases_la_puerta_solo_se_abre_al_liberar_el_ultimo() = runTest {
        val env = Env(this, withGate = true)
        val a = env.gate.acquire(PauseReason.STUDY)
        val b = env.gate.acquire(PauseReason.SIGN_OUT)
        env.start()
        env.advance(5.seconds)

        a.release()
        env.advance(1.seconds)
        assertEquals(0, env.host.ran)
        b.release()
        env.advance(1.seconds)

        assertEquals(1, env.host.ran)
    }

    @Test
    fun una_puerta_que_caduca_por_inactividad_se_detecta_en_un_reintento() = runTest {
        val env = Env(this, withGate = true)
        env.gate.acquire(PauseReason.STUDY) // nadie la libera ni la renueva
        env.start()

        env.advance(STUDY_PAUSE_INACTIVITY_TTL + 1.minutes)

        assertEquals(1, env.host.ran, "tras la caducidad un reintento ve la puerta abierta y sincroniza")
        assertTrue(env.host.calls.last() in STUDY_PAUSE_INACTIVITY_TTL.inWholeMilliseconds..(STUDY_PAUSE_INACTIVITY_TTL + 21.seconds).inWholeMilliseconds)
    }

    // --- primer plano / segundo plano ----------------------------------------------------------------------------------

    @Test
    fun el_periodico_no_corre_en_segundo_plano_y_al_volver_sincroniza() = runTest {
        val env = Env(this)
        env.start()
        env.advance(100.seconds)
        env.foreground.state.value = false
        env.advance(20.minutes)
        assertEquals(listOf(0L), env.host.calls, "en segundo plano y sin pendientes no hay nada que hacer")

        env.foreground.state.value = true
        env.test.runCurrent()

        assertEquals(listOf(0L, (100.seconds + 20.minutes).inWholeMilliseconds), env.host.calls)
    }

    @Test
    fun al_pasar_a_segundo_plano_con_pendientes_hace_un_ultimo_intento_sin_esperar_debounce_ni_intervalo() = runTest {
        val env = Env(this)
        env.start()
        env.advance(5.seconds)
        env.change(1, 100)
        env.advance(1.seconds)

        env.foreground.state.value = false
        env.test.runCurrent()

        assertEquals(listOf(0L, 6.s()), env.host.calls)
    }

    @Test
    fun el_ultimo_intento_en_segundo_plano_se_corta_a_los_10_s() = runTest {
        val env = Env(this)
        env.start()
        env.advance(40.seconds)
        env.change(1, 100)
        env.host.hangMillis = 60.s()

        env.foreground.state.value = false
        env.test.runCurrent()
        env.advance(9.seconds)
        assertEquals(0, env.host.cancelled)
        env.advance(1.seconds)

        assertEquals(1, env.host.cancelled, "cancelado a los 10 s")
        env.advance(10.minutes)
        assertEquals(2, env.host.calls.size, "y no hay mas intentos en segundo plano")
    }

    @Test
    fun el_ultimo_intento_en_segundo_plano_respeta_el_backoff() = runTest {
        val env = Env(this)
        env.host.script = { failure() }
        env.start()                        // fallo a 0 -> backoff hasta 30 s
        env.advance(5.seconds)
        env.change(1, 100)

        env.foreground.state.value = false
        env.advance(20.seconds)
        assertEquals(1, env.host.calls.size)
        env.advance(5.seconds)

        assertEquals(listOf(0L, 30.s()), env.host.calls)
    }

    // --- sin bucles ----------------------------------------------------------------------------------------------------

    @Test
    fun una_huella_que_no_cambia_o_con_count_0_no_dispara_otro_sync() = runTest {
        val env = Env(this)
        env.start()
        env.advance(40.seconds)
        env.change(1, 100)
        env.advance(10.seconds)
        assertEquals(2, env.host.calls.size)

        env.change(1, 100)       // misma huella: no es un cambio
        env.change(0, 0)         // todo subido
        env.change(0, 500)       // count 0: nunca dispara
        env.advance(4.minutes)

        assertEquals(2, env.host.calls.size, "nada mas hasta el periodico")
    }

    @Test
    fun terminar_de_subir_cancela_un_debounce_pendiente() = runTest {
        val env = Env(this)
        env.start()
        env.advance(40.seconds)

        env.change(1, 100)
        env.advance(2.seconds)
        env.change(0, 0)         // un sync manual lo subio todo antes de que venza el debounce
        env.advance(10.seconds)

        assertEquals(listOf(0L), env.host.calls)
    }
}
