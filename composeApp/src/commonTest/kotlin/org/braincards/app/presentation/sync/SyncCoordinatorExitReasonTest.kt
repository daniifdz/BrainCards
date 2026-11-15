package org.braincards.app.presentation.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.braincards.app.domain.models.AuthState
import org.braincards.app.presentation.screens.auth.AuthVmEnv
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Motivo de la salida del usuario (SignedOut / AccountDeleted) y marca de "salida esperada" (la peticion de cierre o
 * borrado esta en curso): lo que AppEntryViewModel necesita para elegir el aviso de la Bienvenida.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorExitReasonTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val signedIn = AuthState.Authenticated("user-1", "a@b.co", sessionConfirmed = false)

    // --- motivo ----------------------------------------------------------------------------------------------------

    @Test
    fun al_empezar_no_hay_motivo_ni_marca() = runTest {
        val env = AuthVmEnv()
        assertNull(env.coordinator.userSessionExitReason.value)
        assertNull(env.coordinator.expectedUserExit.value)
    }

    @Test
    fun el_cierre_de_sesion_publica_SignedOut() = runTest {
        val env = AuthVmEnv(signedIn)

        env.vm.requestSignOut()

        assertEquals(SessionExitReason.SignedOut, env.coordinator.userSessionExitReason.value)
        assertEquals(1, env.coordinator.userSessionExitCount.value)
        assertNull(env.coordinator.expectedUserExit.value, "la marca se limpia al terminar")
    }

    @Test
    fun borrar_la_cuenta_publica_AccountDeleted() = runTest {
        val env = AuthVmEnv(signedIn)

        env.vm.deleteAccount()

        assertEquals(SessionExitReason.AccountDeleted, env.coordinator.userSessionExitReason.value)
        assertEquals(1, env.coordinator.userSessionExitCount.value)
        assertNull(env.coordinator.expectedUserExit.value)
    }

    @Test
    fun un_reset_sin_motivo_borra_el_motivo_anterior_y_sube_el_contador() = runTest {
        val env = AuthVmEnv()
        env.coordinator.resetSyncState(SessionExitReason.SignedOut)

        env.coordinator.resetSyncState()

        assertNull(env.coordinator.userSessionExitReason.value, "un motivo viejo no puede colarse en la salida siguiente")
        assertEquals(2, env.coordinator.userSessionExitCount.value)
    }

    @Test
    fun el_motivo_se_escribe_antes_de_subir_el_contador() = runTest {
        val env = AuthVmEnv()
        env.coordinator.resetSyncState(SessionExitReason.SignedOut) // motivo viejo que un orden incorrecto dejaria ver
        val seenAtCountChange = mutableListOf<SessionExitReason?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            env.coordinator.userSessionExitCount.drop(1).collect {
                seenAtCountChange += env.coordinator.userSessionExitReason.value
            }
        }

        env.coordinator.resetSyncState(SessionExitReason.AccountDeleted)

        assertEquals(listOf<SessionExitReason?>(SessionExitReason.AccountDeleted), seenAtCountChange)
    }

    // --- SessionCloseFailed y fallos: no son una salida -------------------------------------------------------------

    @Test
    fun SessionCloseFailed_sube_el_contador_como_siempre_pero_sin_motivo() = runTest {
        val env = AuthVmEnv(signedIn)
        env.auth.signOutFailure = RuntimeException("sin red")
        env.auth.clearLocalSessionFailure = RuntimeException("sin acceso a la sesion")

        env.vm.requestSignOut()

        assertEquals(1, env.coordinator.userSessionExitCount.value)
        assertNull(env.coordinator.userSessionExitReason.value)
        assertNull(env.coordinator.expectedUserExit.value, "la sesion sigue viva: no hay salida esperada")
    }

    @Test
    fun si_el_borrado_local_falla_no_hay_motivo_ni_marca_ni_contador() = runTest {
        val env = AuthVmEnv(signedIn)
        env.local.wipeFailure = IllegalStateException("disco lleno")

        env.vm.requestSignOut()

        assertEquals(0, env.coordinator.userSessionExitCount.value)
        assertNull(env.coordinator.userSessionExitReason.value)
        assertNull(env.coordinator.expectedUserExit.value)
    }

    @Test
    fun si_el_borrado_de_cuenta_falla_se_limpia_la_marca() = runTest {
        val env = AuthVmEnv(signedIn)
        env.auth.deleteAccountFailure = IllegalStateException("x")

        env.vm.deleteAccount()

        assertEquals(0, env.coordinator.userSessionExitCount.value)
        assertNull(env.coordinator.userSessionExitReason.value)
        assertNull(env.coordinator.expectedUserExit.value)
    }

    // --- marca de "salida esperada" ---------------------------------------------------------------------------------

    @Test
    fun el_cierre_de_sesion_marca_la_salida_esperada_mientras_la_peticion_esta_en_curso() = runTest {
        val env = AuthVmEnv(signedIn)
        val gate = CompletableDeferred<Unit>()
        env.auth.signOutGate = gate // el logout remoto esta "lento": parado en la compuerta

        env.vm.requestSignOut()

        assertEquals(SessionExitReason.SignedOut, env.coordinator.expectedUserExit.value, "marcada ANTES de la llamada")
        assertEquals(0, env.coordinator.userSessionExitCount.value)

        gate.complete(Unit)

        assertNull(env.coordinator.expectedUserExit.value, "limpiada al terminar (resetSyncState)")
        assertEquals(SessionExitReason.SignedOut, env.coordinator.userSessionExitReason.value)
    }

    @Test
    fun borrar_la_cuenta_marca_la_salida_esperada_mientras_la_peticion_esta_en_curso() = runTest {
        val env = AuthVmEnv(signedIn)
        val gate = CompletableDeferred<Unit>()
        env.auth.signOutGate = gate // DeleteAccountUseCase acaba con signOut(): aqui se queda parado

        env.vm.deleteAccount()

        assertEquals(SessionExitReason.AccountDeleted, env.coordinator.expectedUserExit.value)

        gate.complete(Unit)

        assertNull(env.coordinator.expectedUserExit.value)
        assertEquals(SessionExitReason.AccountDeleted, env.coordinator.userSessionExitReason.value)
    }

    @Test
    fun si_la_llamada_de_cierre_se_interrumpe_con_una_excepcion_la_marca_se_limpia_y_se_relanza() = runTest {
        val env = AuthVmEnv(signedIn)
        env.local.wipeFailure = Error("no es una Exception: atraviesa el caso WipeFailed")

        assertFailsWith<Error> { env.coordinator.finishSignOut("user-1", 0) }

        assertNull(env.coordinator.expectedUserExit.value)
        assertEquals(0, env.coordinator.userSessionExitCount.value)
    }
}
