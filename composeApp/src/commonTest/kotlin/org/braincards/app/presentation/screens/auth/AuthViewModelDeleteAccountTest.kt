package org.braincards.app.presentation.screens.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.braincards.app.domain.models.AuthState
import org.braincards.app.presentation.sync.SessionExitReason
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Borrado de cuenta en AuthViewModel (Ajustes): fallo remoto, sesion que no se puede cerrar tras borrar, exito y
 * doble toque. Los eventos son tipos (DeleteAccountEvent); los textos los resuelve SettingsScreen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelDeleteAccountTest {

    private val signedIn = AuthState.Authenticated("user-1", "a@b.co", sessionConfirmed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.record(env: AuthVmEnv): MutableList<DeleteAccountEvent> {
        val events = mutableListOf<DeleteAccountEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.deleteAccountEvent.collect { events += it } }
        return events
    }

    @Test
    fun si_el_borrado_remoto_falla_avisa_Failed_y_limpia_la_marca_de_salida_esperada() = runTest {
        val env = AuthVmEnv(signedIn)
        val events = record(env)
        env.auth.deleteAccountFailure = RuntimeException("sin red")

        env.vm.deleteAccount()

        assertEquals(listOf(DeleteAccountEvent.Failed), events)
        assertNull(env.coordinator.expectedUserExit.value, "la marca de salida esperada no puede quedarse puesta")
        assertEquals(0, env.coordinator.userSessionExitCount.value)
        assertFalse(env.auth.signOutCalled)
        assertEquals(signedIn, env.auth.authState.first())
        assertFalse(env.vm.isDeletingAccount.value)
    }

    @Test
    fun si_tras_borrar_no_se_puede_cerrar_la_sesion_avisa_SessionNotClosed_y_no_es_una_salida() = runTest {
        val env = AuthVmEnv(signedIn)
        val events = record(env)
        env.auth.signOutFailure = RuntimeException("sin red")
        env.auth.clearLocalSessionFailure = IllegalStateException("disco")

        env.vm.deleteAccount()

        assertEquals(listOf(DeleteAccountEvent.SessionNotClosed), events)
        // Sin salida confirmada (la sesion sigue viva): el contador sube, pero SIN motivo, y la marca se limpia.
        assertNull(env.coordinator.userSessionExitReason.value)
        assertEquals(1, env.coordinator.userSessionExitCount.value)
        assertNull(env.coordinator.expectedUserExit.value)
        assertEquals(signedIn, env.auth.authState.first())
        assertFalse(env.vm.isDeletingAccount.value)
    }

    @Test
    fun el_exito_es_una_salida_AccountDeleted_sin_eventos_en_Ajustes() = runTest {
        val env = AuthVmEnv(signedIn)
        val events = record(env)

        env.vm.deleteAccount()

        assertEquals(emptyList(), events)
        assertEquals(SessionExitReason.AccountDeleted, env.coordinator.userSessionExitReason.value)
        assertEquals(1, env.coordinator.userSessionExitCount.value)
        assertNull(env.coordinator.expectedUserExit.value)
        assertEquals(AuthState.NotAuthenticated, env.auth.authState.first())
    }

    @Test
    fun si_signOut_falla_pero_el_respaldo_local_funciona_es_una_salida_normal() = runTest {
        val env = AuthVmEnv(signedIn)
        val events = record(env)
        env.auth.signOutFailure = RuntimeException("sin red")

        env.vm.deleteAccount()

        assertEquals(emptyList(), events)
        assertTrue(env.auth.clearLocalSessionCalled)
        assertEquals(SessionExitReason.AccountDeleted, env.coordinator.userSessionExitReason.value)
        assertEquals(AuthState.NotAuthenticated, env.auth.authState.first())
    }

    @Test
    fun mientras_se_borra_isDeletingAccount_es_true_y_un_segundo_toque_no_repite_el_borrado() = runTest {
        val env = AuthVmEnv(signedIn)
        val gate = CompletableDeferred<Unit>()
        env.auth.signOutGate = gate

        env.vm.deleteAccount()
        assertTrue(env.vm.isDeletingAccount.value)
        env.vm.deleteAccount()
        gate.complete(Unit)

        assertEquals(1, env.auth.deleteAccountCallCount)
        assertFalse(env.vm.isDeletingAccount.value)
    }
}
