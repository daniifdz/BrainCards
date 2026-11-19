package org.braincards.app.platform

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopSyncMonitorsTest {

    @Test
    fun la_ventana_arranca_en_primer_plano() {
        assertTrue(DesktopForegroundMonitor().isForeground.value)
    }

    @Test
    fun minimizar_la_ventana_la_pasa_a_segundo_plano_y_restaurarla_a_primer_plano() {
        val monitor = DesktopForegroundMonitor()

        monitor.onWindowMinimizedChanged(true)
        assertFalse(monitor.isForeground.value)
        monitor.onWindowMinimizedChanged(false)

        assertTrue(monitor.isForeground.value)
    }

    @Test
    fun el_valor_inicial_puede_ser_ventana_no_visible() {
        assertFalse(DesktopForegroundMonitor(initiallyVisible = false).isForeground.value)
    }

    @Test
    fun el_valor_inicial_es_sincrono_sin_esperar_al_primer_sondeo() = runTest {
        val monitor = DesktopConnectivityMonitor(backgroundScope, probe = { false })

        assertFalse(monitor.isOnline.value, "disponible nada mas crearlo, sin avanzar el tiempo")
    }

    @Test
    fun sondea_cada_10_s_y_no_antes() = runTest {
        var online = true
        var probes = 0
        val monitor = DesktopConnectivityMonitor(backgroundScope, probe = { probes++; online })
        runCurrent()
        assertEquals(1, probes, "solo el sondeo inicial")

        online = false
        advanceTimeBy(9.seconds)
        runCurrent()
        assertTrue(monitor.isOnline.value, "a los 9 s aun no ha vuelto a mirar")
        assertEquals(1, probes)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertFalse(monitor.isOnline.value)
        assertEquals(2, probes)

        advanceTimeBy(20.seconds)
        runCurrent()
        assertEquals(4, probes, "uno cada 10 s")
    }

    @Test
    fun recupera_el_online_en_el_siguiente_sondeo() = runTest {
        var online = false
        val monitor = DesktopConnectivityMonitor(backgroundScope, probe = { online })
        assertFalse(monitor.isOnline.value)

        online = true
        advanceTimeBy(10.seconds)
        runCurrent()

        assertTrue(monitor.isOnline.value)
    }

    @Test
    fun si_el_sondeo_falla_conserva_el_ultimo_valor() = runTest {
        var mode = "ok"
        val monitor = DesktopConnectivityMonitor(backgroundScope, probe = {
            if (mode == "falla") error("sondeo caido") else mode == "ok"
        })
        assertTrue(monitor.isOnline.value)

        mode = "falla"
        advanceTimeBy(10.seconds)
        runCurrent()
        assertTrue(monitor.isOnline.value, "un fallo del sondeo no cambia el estado")

        mode = "sin red"
        advanceTimeBy(10.seconds)
        runCurrent()
        assertFalse(monitor.isOnline.value)
    }

    @Test
    fun si_el_sondeo_inicial_falla_no_bloquea_el_sync_y_empieza_online() = runTest {
        val monitor = DesktopConnectivityMonitor(backgroundScope, probe = { error("sin permisos") })

        assertTrue(monitor.isOnline.value)
    }

    @Test
    fun close_para_el_sondeo() = runTest {
        var online = true
        var probes = 0
        val monitor = DesktopConnectivityMonitor(backgroundScope, probe = { probes++; online })
        runCurrent()

        monitor.close()
        online = false
        advanceTimeBy(60.seconds)
        runCurrent()

        assertEquals(1, probes, "ningun sondeo mas tras close()")
        assertTrue(monitor.isOnline.value)
    }
}
