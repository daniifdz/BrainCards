package org.braincards.app.presentation.screens.navigation

import org.braincards.app.domain.models.AuthState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Funciones puras de la navegacion de entrada. */
class StartDestinationTest {

    private val confirmed = AuthState.Authenticated("u", "a@b.co", sessionConfirmed = true)
    private val unconfirmed = AuthState.Authenticated("u", "a@b.co", sessionConfirmed = false)

    // --- resolveStartDestination ----------------------------------------------------------------------------------

    @Test
    fun sin_estado_estable_es_Pending_con_cualquier_flag() {
        listOf<Boolean?>(null, false, true).forEach { flag ->
            assertEquals(EntryRoute.Pending, resolveStartDestination(null, flag), "flag=$flag")
        }
    }

    @Test
    fun Loading_cuenta_como_sin_estado_estable() {
        listOf<Boolean?>(null, false, true).forEach { flag ->
            assertEquals(EntryRoute.Pending, resolveStartDestination(AuthState.Loading, flag), "flag=$flag")
        }
    }

    @Test
    fun con_sesion_siempre_Home_confirmada_o_no_y_sin_esperar_al_flag() {
        listOf<Boolean?>(null, false, true).forEach { flag ->
            assertEquals(EntryRoute.Home, resolveStartDestination(confirmed, flag), "confirmada, flag=$flag")
            assertEquals(EntryRoute.Home, resolveStartDestination(unconfirmed, flag), "sin confirmar, flag=$flag")
        }
    }

    @Test
    fun sin_sesion_y_flag_sin_leer_es_Pending() {
        assertEquals(EntryRoute.Pending, resolveStartDestination(AuthState.NotAuthenticated, null))
    }

    @Test
    fun sin_sesion_y_modo_local_elegido_es_Home() {
        assertEquals(EntryRoute.Home, resolveStartDestination(AuthState.NotAuthenticated, true))
    }

    @Test
    fun sin_sesion_y_sin_elegir_modo_local_es_Welcome() {
        assertEquals(EntryRoute.Welcome, resolveStartDestination(AuthState.NotAuthenticated, false))
    }

    // --- authExitAction -------------------------------------------------------------------------------------------

    @Test
    fun la_bienvenida_de_entrada_sale_a_Home() {
        assertEquals(AuthExit.GoHome, authExitAction(asEntry = true))
    }

    @Test
    fun la_bienvenida_apilada_vuelve_al_origen() {
        assertEquals(AuthExit.PopToOrigin, authExitAction(asEntry = false))
    }

    @Test
    fun si_la_entrada_de_Auth_no_esta_en_la_pila_la_salida_segura_es_Home() {
        assertEquals(AuthExit.GoHome, authExitAction(asEntry = null))
    }

    // --- guardia de pila restaurada -------------------------------------------------------------------------------

    @Test
    fun guardia_solo_si_el_arranque_es_Welcome_y_la_pila_no_esta_en_un_flujo_de_auth() {
        assertEquals(true, needsWelcomeGuard(EntryRoute.Welcome, currentIsAuthFlow = false))
        assertEquals(false, needsWelcomeGuard(EntryRoute.Welcome, currentIsAuthFlow = true))
        assertEquals(false, needsWelcomeGuard(EntryRoute.Home, currentIsAuthFlow = false))
        assertEquals(false, needsWelcomeGuard(EntryRoute.Pending, currentIsAuthFlow = false))
    }
}
