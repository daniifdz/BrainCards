package org.braincards.app.presentation.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * userSessionExitCount: sube cuando termina un cierre de sesion o un borrado de cuenta del USUARIO (los dos llaman a
 * resetSyncState al acabar bien). El arranque lo usa para no esperar el debounce de sesion perdida.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorUserExitTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun empieza_en_cero_y_resetSyncState_lo_sube() = runTest {
        val env = AuthVmEnv()
        assertEquals(0, env.coordinator.userSessionExitCount.value)

        env.coordinator.resetSyncState()
        assertEquals(1, env.coordinator.userSessionExitCount.value)

        env.coordinator.resetSyncState()
        assertEquals(2, env.coordinator.userSessionExitCount.value)
    }

    @Test
    fun borrar_la_cuenta_con_exito_lo_sube() = runTest {
        val env = AuthVmEnv(AuthState.Authenticated("user-1", "a@b.co", sessionConfirmed = true))

        env.vm.deleteAccount()

        assertEquals(1, env.coordinator.userSessionExitCount.value)
    }

    @Test
    fun si_el_borrado_de_cuenta_falla_no_cuenta_como_salida_del_usuario() = runTest {
        val env = AuthVmEnv(AuthState.Authenticated("user-1", "a@b.co", sessionConfirmed = true))
        env.auth.deleteAccountFailure = IllegalStateException("x")

        env.vm.deleteAccount()

        assertEquals(0, env.coordinator.userSessionExitCount.value)
    }

    @Test
    fun el_cierre_de_sesion_sin_pendientes_lo_sube() = runTest {
        val env = AuthVmEnv(AuthState.Authenticated("user-1", "a@b.co", sessionConfirmed = false))

        env.vm.requestSignOut()

        assertEquals(1, env.coordinator.userSessionExitCount.value)
    }
}
