package org.braincards.app.presentation.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Maquina de estados de la red por defecto de Android: cambios de red, onLost tardio, perdida de INTERNET. */
class DefaultNetworkTrackerTest {

    private val wifi = 1L
    private val mobile = 2L
    private val other = 3L

    // --- valor inicial ---

    @Test
    fun sin_red_activa_empieza_offline() {
        assertFalse(DefaultNetworkTracker().isOnline)
        assertFalse(DefaultNetworkTracker(activeNetwork = null, activeHasInternet = true).isOnline)
    }

    @Test
    fun con_red_activa_con_internet_empieza_online() {
        assertTrue(DefaultNetworkTracker(activeNetwork = wifi, activeHasInternet = true).isOnline)
    }

    @Test
    fun con_red_activa_sin_internet_empieza_offline() {
        assertFalse(DefaultNetworkTracker(activeNetwork = wifi, activeHasInternet = false).isOnline)
    }

    // --- cambio de red ---

    @Test
    fun el_onLost_tardio_de_la_red_antigua_no_pone_offline_tras_el_cambio_de_wifi_a_datos() {
        val tracker = DefaultNetworkTracker(wifi, true)

        tracker.onAvailable(mobile)   // llega primero la nueva...
        tracker.onCapabilitiesChanged(mobile, true)
        tracker.onLost(wifi)          // ...y el onLost de la antigua despues

        assertTrue(tracker.isOnline, "la red actual (datos) sigue disponible")
    }

    @Test
    fun el_orden_inverso_onLost_y_luego_onAvailable_termina_online() {
        val tracker = DefaultNetworkTracker(wifi, true)

        tracker.onLost(wifi)
        assertFalse(tracker.isOnline, "entre medias no hay ninguna red")
        tracker.onAvailable(mobile)

        assertTrue(tracker.isOnline)
    }

    @Test
    fun las_capacidades_de_una_red_que_ya_no_es_la_actual_se_ignoran() {
        val tracker = DefaultNetworkTracker(wifi, true)
        tracker.onAvailable(mobile)

        tracker.onCapabilitiesChanged(wifi, false) // la antigua pierde INTERNET justo antes de caer

        assertTrue(tracker.isOnline)
    }

    @Test
    fun tras_el_cambio_la_perdida_real_de_la_red_nueva_si_pone_offline() {
        val tracker = DefaultNetworkTracker(wifi, true)
        tracker.onAvailable(mobile)
        tracker.onLost(wifi)

        tracker.onLost(mobile)

        assertFalse(tracker.isOnline)
    }

    // --- perdida y recuperacion ---

    @Test
    fun la_perdida_de_la_red_actual_pone_offline_y_una_nueva_la_recupera() {
        val tracker = DefaultNetworkTracker(wifi, true)

        tracker.onLost(wifi)
        assertFalse(tracker.isOnline)
        tracker.onAvailable(other)

        assertTrue(tracker.isOnline)
    }

    @Test
    fun perder_la_capacidad_de_internet_de_la_red_actual_la_deja_offline_y_recuperarla_online() {
        val tracker = DefaultNetworkTracker(wifi, true)

        tracker.onCapabilitiesChanged(wifi, false)
        assertFalse(tracker.isOnline)
        tracker.onCapabilitiesChanged(wifi, true)

        assertTrue(tracker.isOnline)
    }

    @Test
    fun un_onLost_de_una_red_desconocida_o_ya_perdida_se_ignora() {
        val tracker = DefaultNetworkTracker(wifi, true)

        tracker.onLost(other)
        assertTrue(tracker.isOnline)

        tracker.onLost(wifi)
        tracker.onAvailable(mobile)
        tracker.onLost(wifi) // duplicado de la antigua
        assertTrue(tracker.isOnline)
    }

    @Test
    fun sin_red_actual_unas_capacidades_con_internet_la_adoptan() {
        val tracker = DefaultNetworkTracker()

        tracker.onCapabilitiesChanged(wifi, true)

        assertTrue(tracker.isOnline)
    }

    @Test
    fun onAvailable_de_la_misma_red_dos_veces_no_cambia_nada() {
        val tracker = DefaultNetworkTracker()

        tracker.onAvailable(wifi)
        tracker.onAvailable(wifi)

        assertEquals(true, tracker.isOnline)
    }
}
