package org.braincards.app.presentation.screens.navigation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.LocalModePreference
import org.braincards.app.presentation.sync.SessionExitReason
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [writeDelayMs] > 0 hace que la escritura TARDE (suspenda): asi el orden "borrar el flag y despues avisar" es observable. */
internal class FakeLocalModePreference(initial: Boolean = false, private val writeDelayMs: Long = 0) : LocalModePreference {
    val state = MutableStateFlow(initial)
    override val chosen = state
    override suspend fun setChosen(value: Boolean) {
        if (writeDelayMs > 0) delay(writeDelayMs)
        state.value = value
    }
}

/** Arranque y sesion perdida: tiempo virtual, sin esperas reales. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppEntryViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val signedIn = AuthState.Authenticated("u1", "a@b.co", sessionConfirmed = true)

    private class Env(initialAuth: AuthState, initialFlag: Boolean = false, prefWriteDelayMs: Long = 0) {
        val auth = FakeAuthRepository(initialAuth)
        val pref = FakeLocalModePreference(initialFlag, prefWriteDelayMs)
        val exitCount = MutableStateFlow(0)
        val exitReason = MutableStateFlow<SessionExitReason?>(null)
        val expectedExit = MutableStateFlow<SessionExitReason?>(null)
        val vm = AppEntryViewModel(auth, pref, exitCount, exitReason, expectedExit)

        /** Lo que hace SyncCoordinator.resetSyncState: primero el motivo y despues el contador. */
        fun userExit(reason: SessionExitReason? = SessionExitReason.SignedOut) {
            exitReason.value = reason
            exitCount.value += 1
        }
    }

    /** Recoge los eventos signedOut; guarda el valor del flag EN EL MOMENTO de recibir cada uno. */
    private fun TestScope.collectSignedOut(env: Env): List<Boolean> {
        val flagAtEvent = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            env.vm.signedOut.collect { flagAtEvent += env.pref.state.value }
        }
        return flagAtEvent
    }

    // --- ruta de arranque -----------------------------------------------------------------------------------------

    @Test
    fun mientras_auth_esta_en_Loading_la_ruta_es_Pending() = runTest {
        val env = Env(AuthState.Loading)
        assertEquals(EntryRoute.Pending, env.vm.startRoute.value)
    }

    @Test
    fun sin_sesion_y_sin_modo_local_arranca_en_Welcome() = runTest {
        val env = Env(AuthState.Loading)

        env.auth.setState(AuthState.NotAuthenticated)

        assertEquals(EntryRoute.Welcome, env.vm.startRoute.value)
    }

    @Test
    fun con_sesion_arranca_en_Home() = runTest {
        assertEquals(EntryRoute.Home, Env(signedIn).vm.startRoute.value)
    }

    @Test
    fun sin_sesion_con_modo_local_elegido_arranca_en_Home() = runTest {
        assertEquals(EntryRoute.Home, Env(AuthState.NotAuthenticated, initialFlag = true).vm.startRoute.value)
    }

    @Test
    fun la_ruta_de_arranque_se_fija_con_el_primer_valor_resuelto() = runTest {
        val env = Env(signedIn)
        assertEquals(EntryRoute.Home, env.vm.startRoute.value)

        env.auth.setState(AuthState.NotAuthenticated) // sesion perdida: eso es un evento, no cambia el arranque
        advanceTimeBy(5_000); runCurrent()

        assertEquals(EntryRoute.Home, env.vm.startRoute.value)
    }

    @Test
    fun un_Loading_al_volver_de_segundo_plano_no_cambia_la_ruta() = runTest {
        val env = Env(signedIn)

        env.auth.setState(AuthState.Loading)

        assertEquals(EntryRoute.Home, env.vm.startRoute.value)
    }

    @Test
    fun elegir_modo_local_activa_el_flag() = runTest {
        val env = Env(AuthState.NotAuthenticated)

        env.vm.chooseLocalMode()

        assertTrue(env.pref.state.value)
    }

    // --- sesion perdida -------------------------------------------------------------------------------------------

    @Test
    fun el_arranque_sin_sesion_no_es_una_sesion_perdida() = runTest {
        val env = Env(AuthState.NotAuthenticated)
        val events = collectSignedOut(env)

        advanceTimeBy(10_000); runCurrent()

        assertTrue(events.isEmpty())
    }

    @Test
    fun una_caida_que_no_pidio_el_usuario_espera_el_debounce_y_despues_avisa_una_vez() = runTest {
        val env = Env(signedIn, initialFlag = true)
        val events = collectSignedOut(env)

        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds - 1); runCurrent()
        assertTrue(events.isEmpty(), "aun dentro del debounce")

        advanceTimeBy(1); runCurrent()
        assertEquals(1, events.size)
    }

    @Test
    fun el_flag_ya_esta_borrado_cuando_llega_el_evento() = runTest {
        // La escritura del flag tarda 50 ms: si el evento se enviara antes de terminarla, el colector veria el flag a true.
        val env = Env(signedIn, initialFlag = true, prefWriteDelayMs = 50)
        val flagAtEvent = collectSignedOut(env)

        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds + 50); runCurrent()

        assertEquals(listOf(false), flagAtEvent, "el flag se borra ANTES de avisar")
    }

    @Test
    fun un_cierre_de_sesion_del_usuario_avisa_sin_esperar_cuando_el_contador_sube_tras_la_caida() = runTest {
        val env = Env(signedIn, initialFlag = true)
        val events = collectSignedOut(env)

        env.auth.setState(AuthState.NotAuthenticated)
        assertTrue(events.isEmpty())
        env.userExit() // SyncCoordinator.resetSyncState al terminar el cierre / el borrado
        runCurrent()

        assertEquals(1, events.size, "sin esperar el debounce")
    }

    @Test
    fun un_cierre_del_usuario_cuyo_contador_sube_antes_de_ver_la_caida_tampoco_espera() = runTest {
        val env = Env(signedIn, initialFlag = true)
        val events = collectSignedOut(env)

        env.userExit()
        env.auth.setState(AuthState.NotAuthenticated)
        runCurrent()

        assertEquals(1, events.size)
    }

    @Test
    fun un_Loading_con_sesion_no_es_una_caida() = runTest {
        val env = Env(signedIn, initialFlag = true)
        val events = collectSignedOut(env)

        env.auth.setState(AuthState.Loading)
        env.auth.setState(signedIn)
        advanceTimeBy(10_000); runCurrent()

        assertTrue(events.isEmpty())
        assertTrue(env.pref.state.value, "el flag no se toca")
    }

    @Test
    fun una_caida_que_se_recupera_dentro_del_debounce_ni_avisa_ni_toca_el_flag() = runTest {
        val env = Env(signedIn, initialFlag = true)
        val events = collectSignedOut(env)

        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(1_000); runCurrent()
        env.auth.setState(signedIn)
        advanceTimeBy(10_000); runCurrent()

        assertTrue(events.isEmpty())
        assertTrue(env.pref.state.value)
    }

    @Test
    fun una_caida_a_traves_de_un_Loading_sigue_siendo_una_caida() = runTest {
        val env = Env(signedIn, initialFlag = true)
        val events = collectSignedOut(env)

        env.auth.setState(AuthState.Loading)
        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds); runCurrent()

        assertEquals(1, events.size)
    }

    @Test
    fun cambiar_de_sesion_confirmada_a_sin_confirmar_no_es_una_caida() = runTest {
        val env = Env(signedIn, initialFlag = true)
        val events = collectSignedOut(env)

        env.auth.setState(AuthState.Authenticated("u1", "a@b.co", sessionConfirmed = false))
        advanceTimeBy(10_000); runCurrent()

        assertTrue(events.isEmpty())
    }

    // --- consumo unico del evento ---------------------------------------------------------------------------------

    @Test
    fun el_evento_se_consume_una_sola_vez_un_colector_nuevo_no_lo_recibe_otra_vez() = runTest {
        val env = Env(signedIn, initialFlag = true)
        val first = collectSignedOut(env)
        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds); runCurrent()
        assertEquals(1, first.size)

        // Recomponer / rotar: aparece un colector nuevo.
        val second = collectSignedOut(env)
        advanceTimeBy(10_000); runCurrent()

        assertTrue(second.isEmpty())
        assertEquals(1, first.size)
    }

    @Test
    fun si_no_hay_colector_en_ese_momento_el_evento_espera_y_se_entrega_una_sola_vez() = runTest {
        val env = Env(signedIn, initialFlag = true)

        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds); runCurrent()
        val late = collectSignedOut(env)
        runCurrent()
        assertEquals(1, late.size)

        val later = collectSignedOut(env)
        runCurrent()
        assertTrue(later.isEmpty())
    }
}
