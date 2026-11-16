package org.braincards.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReducedMotionTest {
    @Test
    fun escala_cero_es_movimiento_reducido() = assertTrue(isReducedMotion(0f))

    @Test
    fun cualquier_escala_positiva_anima() {
        assertFalse(isReducedMotion(1f))
        assertFalse(isReducedMotion(0.5f))
        assertFalse(isReducedMotion(10f))
    }

    @Test
    fun si_falta_el_ajuste_el_valor_por_defecto_anima() {
        assertEquals(1f, DEFAULT_ANIMATOR_DURATION_SCALE)
        assertFalse(isReducedMotion(DEFAULT_ANIMATOR_DURATION_SCALE))
    }
}
