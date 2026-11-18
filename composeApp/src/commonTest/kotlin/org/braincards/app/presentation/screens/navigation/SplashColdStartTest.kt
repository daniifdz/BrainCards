package org.braincards.app.presentation.screens.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Decision de la animacion en el arranque en frio de Android (13b2): proceso nuevo, sin estado guardado, Android >= 12. */
class SplashColdStartTest {

    private fun play(enabled: Boolean = true, sdk: Int = 34, saved: Boolean = false, fresh: Boolean = true) =
        shouldPlayColdStartSplash(enabled, sdk, saved, fresh)

    @Test
    fun arranque_en_frio_normal_si_se_anima() = assertTrue(play())

    @Test
    fun con_el_interruptor_apagado_no_se_anima_nunca() {
        assertFalse(play(enabled = false))
        assertFalse(play(enabled = false, sdk = 36))
    }

    @Test
    fun por_debajo_de_android_12_todo_queda_como_estaba() {
        assertFalse(play(sdk = 30))
        assertFalse(play(sdk = 24))
        assertTrue(play(sdk = 31), "31 es el primero con splash propio")
        assertEquals(31, SPLASH_COLD_START_MIN_SDK)
    }

    @Test
    fun rotacion_y_restauracion_tras_muerte_de_proceso_no_animan() {
        assertFalse(play(saved = true, fresh = true), "proceso nuevo pero con estado guardado = restauracion")
        assertFalse(play(saved = true, fresh = false), "rotacion")
    }

    @Test
    fun arranque_en_caliente_no_anima() = assertFalse(play(fresh = false, saved = false))

    @Test
    fun el_interruptor_de_fabrica_esta_encendido_y_el_plan_B_es_un_tiempo_razonable() {
        assertTrue(SPLASH_ANIMATE_ON_COLD_START)
        assertTrue(SPLASH_EXIT_SAFETY_MS in 500L..2_000L, "plan B = $SPLASH_EXIT_SAFETY_MS ms")
    }

    @Test
    fun la_senal_de_salida_empieza_apagada_y_se_enciende_una_vez() {
        val s = SplashExitSignal()
        assertFalse(s.fired)
        s.fire()
        assertTrue(s.fired)
        s.fire()
        assertTrue(s.fired)
    }
}
