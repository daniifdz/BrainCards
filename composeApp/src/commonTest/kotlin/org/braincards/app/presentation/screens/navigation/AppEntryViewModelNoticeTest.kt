package org.braincards.app.presentation.screens.navigation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.braincards.app.presentation.screens.auth.AuthVmEnv
import org.braincards.app.presentation.sync.SessionExitReason
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Aviso de la Bienvenida tras salir de la sesion ([WelcomeNotice]): motivo correcto por flujo, un solo uso, correo en
 * memoria, debounce de la sesion perdida y salida esperada. Tiempo virtual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppEntryViewModelNoticeTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun signedIn(email: String? = "a@b.co") = AuthState.Authenticated("u1", email, sessionConfirmed = true)

    private class Env(initialAuth: AuthState) {
        val auth = FakeAuthRepository(initialAuth)
        val pref = FakeLocalModePreference(initial = true)
        val exitCount = MutableStateFlow(0)
        val exitReason = MutableStateFlow<SessionExitReason?>(null)
        val expectedExit = MutableStateFlow<SessionExitReason?>(null)
        val vm = AppEntryViewModel(auth, pref, exitCount, exitReason, expectedExit)

        /** Lo que hace SyncCoordinator.resetSyncState: motivo y despues contador. */
        fun userExit(reason: SessionExitReason?) {
            exitReason.value = reason
            exitCount.value += 1
        }
    }

    /** Cuenta los eventos signedOut y guarda el aviso que habia en el slot al recibir cada uno. */
    private fun TestScope.collectEvents(vm: AppEntryViewModel): List<WelcomeNotice?> {
        val noticeAtEvent = mutableListOf<WelcomeNotice?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.signedOut.collect { noticeAtEvent += vm.welcomeNotice.value }
        }
        return noticeAtEvent
    }

    // --- motivo correcto por flujo --------------------------------------------------------------------------------

    @Test
    fun un_cierre_de_sesion_del_usuario_deja_el_aviso_SignedOut() = runTest {
        val env = Env(signedIn())
        collectEvents(env.vm)

        env.auth.setState(AuthState.NotAuthenticated)
        env.userExit(SessionExitReason.SignedOut)
        runCurrent()

        assertEquals(WelcomeNotice.SignedOut, env.vm.welcomeNotice.value)
    }

    @Test
    fun un_borrado_de_cuenta_deja_el_aviso_AccountDeleted() = runTest {
        val env = Env(signedIn())
        collectEvents(env.vm)

        env.userExit(SessionExitReason.AccountDeleted)
        env.auth.setState(AuthState.NotAuthenticated)
        runCurrent()

        assertEquals(WelcomeNotice.AccountDeleted, env.vm.welcomeNotice.value)
    }

    @Test
    fun una_sesion_perdida_sola_deja_SessionExpired_solo_tras_el_debounce() = runTest {
        val env = Env(signedIn("a@b.co"))
        val events = collectEvents(env.vm)

        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds - 1); runCurrent()
        assertNull(env.vm.welcomeNotice.value, "aun dentro del debounce: ni aviso ni evento")
        assertTrue(events.isEmpty())

        advanceTimeBy(1); runCurrent()
        val notice = env.vm.welcomeNotice.value
        assertIs<WelcomeNotice.SessionExpired>(notice)
        assertEquals("a@b.co", notice.email)
        assertEquals(1, events.size)
    }

    @Test
    fun una_caida_que_se_recupera_dentro_del_debounce_no_deja_aviso() = runTest {
        val env = Env(signedIn())
        collectEvents(env.vm)

        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(1_000); runCurrent()
        env.auth.setState(signedIn())
        advanceTimeBy(10_000); runCurrent()

        assertNull(env.vm.welcomeNotice.value)
    }

    @Test
    fun un_reset_sin_motivo_anterior_a_la_caida_no_salta_el_debounce_ni_es_un_cierre() = runTest {
        // SessionCloseFailed: el contador sube (motivo null) con la sesion aun viva; despues la sesion se pierde de verdad.
        val env = Env(signedIn())
        collectEvents(env.vm)
        env.userExit(null)

        env.auth.setState(AuthState.NotAuthenticated)
        runCurrent()
        assertNull(env.vm.welcomeNotice.value, "sin motivo no se salta la espera")

        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds); runCurrent()
        assertIs<WelcomeNotice.SessionExpired>(env.vm.welcomeNotice.value)
    }

    @Test
    fun la_marca_de_salida_esperada_clasifica_el_cierre_aunque_la_sesion_caiga_antes_de_terminar() = runTest {
        val env = Env(signedIn())
        val events = collectEvents(env.vm)
        env.expectedExit.value = SessionExitReason.SignedOut // cierre en curso (logout lento)

        env.auth.setState(AuthState.NotAuthenticated)
        runCurrent()

        assertEquals(WelcomeNotice.SignedOut, env.vm.welcomeNotice.value, "sin esperar el debounce")
        assertEquals(1, events.size)

        advanceTimeBy(10_000); runCurrent()
        assertEquals(WelcomeNotice.SignedOut, env.vm.welcomeNotice.value, "y nunca se reclasifica como caducada")
        assertEquals(1, events.size)
    }

    @Test
    fun la_marca_de_borrado_de_cuenta_clasifica_AccountDeleted() = runTest {
        val env = Env(signedIn())
        collectEvents(env.vm)
        env.expectedExit.value = SessionExitReason.AccountDeleted

        env.auth.setState(AuthState.NotAuthenticated)
        runCurrent()

        assertEquals(WelcomeNotice.AccountDeleted, env.vm.welcomeNotice.value)
    }

    // --- el aviso ya esta cuando llega el evento de navegacion ---------------------------------------------------------

    @Test
    fun el_aviso_ya_esta_en_el_slot_cuando_llega_signedOut() = runTest {
        val env = Env(signedIn())
        val noticeAtEvent = collectEvents(env.vm)

        env.auth.setState(AuthState.NotAuthenticated)
        env.userExit(SessionExitReason.SignedOut)
        runCurrent()

        assertEquals(listOf<WelcomeNotice?>(WelcomeNotice.SignedOut), noticeAtEvent)
    }

    // --- un solo uso ------------------------------------------------------------------------------------------------

    @Test
    fun el_aviso_en_forma_de_Snackbar_se_consume_una_sola_vez() = runTest {
        val env = Env(signedIn())
        collectEvents(env.vm)
        env.auth.setState(AuthState.NotAuthenticated)
        env.userExit(SessionExitReason.SignedOut)
        runCurrent()

        assertTrue(env.vm.consumeWelcomeNotice(WelcomeNotice.SignedOut))
        assertNull(env.vm.welcomeNotice.value)

        // Recomponer / rotar / volver por otro camino: nadie mas lo recibe.
        assertFalse(env.vm.consumeWelcomeNotice(WelcomeNotice.SignedOut))
        assertNull(env.vm.welcomeNotice.value)
    }

    @Test
    fun consumir_un_aviso_distinto_del_vigente_no_lo_borra() = runTest {
        val env = Env(signedIn())
        collectEvents(env.vm)
        env.auth.setState(AuthState.NotAuthenticated)
        env.userExit(SessionExitReason.AccountDeleted)
        runCurrent()

        assertFalse(env.vm.consumeWelcomeNotice(WelcomeNotice.SignedOut))
        assertEquals(WelcomeNotice.AccountDeleted, env.vm.welcomeNotice.value)
    }

    @Test
    fun un_aviso_nuevo_reemplaza_al_anterior_y_el_ciclo_siguiente_vuelve_a_avisar() = runTest {
        val env = Env(signedIn())
        val events = collectEvents(env.vm)
        env.auth.setState(AuthState.NotAuthenticated)
        env.userExit(SessionExitReason.SignedOut)
        runCurrent()
        assertTrue(env.vm.consumeWelcomeNotice(WelcomeNotice.SignedOut))

        env.auth.setState(signedIn()) // vuelve a iniciar sesion
        env.auth.setState(AuthState.NotAuthenticated)
        env.userExit(SessionExitReason.AccountDeleted)
        runCurrent()

        assertEquals(WelcomeNotice.AccountDeleted, env.vm.welcomeNotice.value)
        assertEquals(2, events.size)
    }

    @Test
    fun el_aviso_persistente_se_queda_hasta_que_se_descarta() = runTest {
        val env = Env(signedIn())
        collectEvents(env.vm)
        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds); runCurrent()
        assertIs<WelcomeNotice.SessionExpired>(env.vm.welcomeNotice.value)

        advanceTimeBy(60_000); runCurrent()
        assertIs<WelcomeNotice.SessionExpired>(env.vm.welcomeNotice.value, "no desaparece solo")

        env.vm.dismissWelcomeNotice() // el usuario interactua con el formulario
        assertNull(env.vm.welcomeNotice.value)
        advanceTimeBy(60_000); runCurrent()
        assertNull(env.vm.welcomeNotice.value, "no reaparece")
    }

    @Test
    fun iniciar_sesion_o_elegir_modo_local_borra_un_aviso_pendiente() = runTest {
        val env = Env(signedIn())
        collectEvents(env.vm)
        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds); runCurrent()
        assertNotNull(env.vm.welcomeNotice.value)

        env.auth.setState(signedIn())
        runCurrent()
        assertNull(env.vm.welcomeNotice.value, "con sesion ningun aviso de salida sigue vigente")

        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds); runCurrent()
        assertNotNull(env.vm.welcomeNotice.value)
        env.vm.chooseLocalMode()
        assertNull(env.vm.welcomeNotice.value)
    }

    @Test
    fun el_arranque_sin_sesion_no_deja_ningun_aviso() = runTest {
        val env = Env(AuthState.NotAuthenticated)

        advanceTimeBy(10_000); runCurrent()

        assertNull(env.vm.welcomeNotice.value)
    }

    // --- correo precargado (solo memoria) -----------------------------------------------------------------------------

    @Test
    fun el_correo_es_el_del_ultimo_estado_autenticado_visto() = runTest {
        val env = Env(signedIn("viejo@b.co"))
        collectEvents(env.vm)
        env.auth.setState(signedIn("nuevo@b.co"))

        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds); runCurrent()

        assertEquals("nuevo@b.co", assertIs<WelcomeNotice.SessionExpired>(env.vm.welcomeNotice.value).email)
    }

    @Test
    fun si_no_se_conoce_el_correo_el_aviso_se_muestra_igual_sin_correo() = runTest {
        val env = Env(signedIn(email = null))
        collectEvents(env.vm)

        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds); runCurrent()

        assertNull(assertIs<WelcomeNotice.SessionExpired>(env.vm.welcomeNotice.value).email)
    }

    @Test
    fun el_correo_no_aparece_en_el_toString_del_aviso_ni_se_escribe_en_la_preferencia() = runTest {
        val env = Env(signedIn("secreto@b.co"))
        collectEvents(env.vm)

        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds); runCurrent()

        val notice = assertIs<WelcomeNotice.SessionExpired>(env.vm.welcomeNotice.value)
        assertFalse("secreto@b.co" in notice.toString(), "el correo no va a logs")
        // La unica preferencia que toca el ViewModel es el flag booleano de modo local: no hay API para guardar texto.
        assertEquals(false, env.pref.state.value)
    }

    @Test
    fun un_arranque_sin_sesion_previa_en_este_proceso_no_inventa_correo_ni_aviso() = runTest {
        // Proceso nuevo con el token ya caducado: nunca se vio un Authenticated, no es una "sesion perdida" en vivo.
        val env = Env(AuthState.NotAuthenticated)
        collectEvents(env.vm)

        advanceTimeBy(10_000); runCurrent()

        assertNull(env.vm.welcomeNotice.value)
    }

    // --- integracion con el SyncCoordinator real ---------------------------------------------------------------------

    private fun integrationVm(env: AuthVmEnv) = AppEntryViewModel(
        authRepository = env.auth,
        localModePreference = FakeLocalModePreference(initial = true),
        userSessionExitCount = env.coordinator.userSessionExitCount,
        userExitReason = env.coordinator.userSessionExitReason,
        expectedUserExit = env.coordinator.expectedUserExit
    )

    private val authenticated = AuthState.Authenticated("user-1", "a@b.co", sessionConfirmed = false)

    @Test
    fun un_cierre_de_sesion_lento_no_se_clasifica_como_sesion_perdida() = runTest {
        val env = AuthVmEnv(authenticated)
        val vm = integrationVm(env)
        collectEvents(vm)
        val gate = CompletableDeferred<Unit>()
        env.auth.signOutGate = gate // el logout remoto tarda; la sesion se da por caida antes de que acabe
        env.vm.requestSignOut()

        env.auth.setState(AuthState.NotAuthenticated)
        runCurrent()
        assertEquals(WelcomeNotice.SignedOut, vm.welcomeNotice.value, "sin esperar el debounce")

        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds * 4); runCurrent()
        assertEquals(WelcomeNotice.SignedOut, vm.welcomeNotice.value, "pasado el debounce sigue siendo un cierre")

        gate.complete(Unit)
        runCurrent()
        assertEquals(WelcomeNotice.SignedOut, vm.welcomeNotice.value)
    }

    @Test
    fun un_borrado_de_cuenta_lento_no_se_clasifica_como_sesion_perdida() = runTest {
        val env = AuthVmEnv(authenticated)
        val vm = integrationVm(env)
        collectEvents(vm)
        val gate = CompletableDeferred<Unit>()
        env.auth.signOutGate = gate
        env.vm.deleteAccount()

        env.auth.setState(AuthState.NotAuthenticated)
        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds * 4); runCurrent()

        assertEquals(WelcomeNotice.AccountDeleted, vm.welcomeNotice.value)
        gate.complete(Unit)
    }

    @Test
    fun un_cierre_normal_con_el_coordinador_real_deja_SignedOut() = runTest {
        val env = AuthVmEnv(authenticated)
        val vm = integrationVm(env)
        collectEvents(vm)

        env.vm.requestSignOut()
        runCurrent()

        assertEquals(WelcomeNotice.SignedOut, vm.welcomeNotice.value)
    }

    @Test
    fun una_caida_sin_peticion_del_usuario_con_el_coordinador_real_es_SessionExpired_con_debounce() = runTest {
        val env = AuthVmEnv(authenticated)
        val vm = integrationVm(env)
        collectEvents(vm)

        env.auth.setState(AuthState.NotAuthenticated)
        runCurrent()
        assertNull(vm.welcomeNotice.value)

        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds); runCurrent()
        assertIs<WelcomeNotice.SessionExpired>(vm.welcomeNotice.value)
    }

    @Test
    fun tras_un_SessionCloseFailed_una_perdida_real_posterior_sigue_siendo_SessionExpired() = runTest {
        val env = AuthVmEnv(authenticated)
        val vm = integrationVm(env)
        collectEvents(vm)
        env.auth.signOutFailure = RuntimeException("sin red")
        env.auth.clearLocalSessionFailure = RuntimeException("sin acceso")
        env.vm.requestSignOut() // BD vacia, sesion viva: contador +1 sin motivo, sin marca

        env.auth.setState(AuthState.NotAuthenticated) // la sesion se revoca despues
        runCurrent()
        assertNull(vm.welcomeNotice.value)

        advanceTimeBy(SESSION_LOSS_DEBOUNCE.inWholeMilliseconds); runCurrent()
        assertIs<WelcomeNotice.SessionExpired>(vm.welcomeNotice.value)
    }
}
