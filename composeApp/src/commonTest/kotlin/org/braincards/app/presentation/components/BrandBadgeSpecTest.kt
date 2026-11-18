package org.braincards.app.presentation.components

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Medidas del badge tomadas de los mockups (`rx="22"`, `scale(0.287)` sobre el lienzo de 230 del SVG de marca). */
class BrandBadgeSpecTest {

    @Test
    fun esquinas_al_22_por_ciento() = assertEquals(22, BADGE_CORNER_PERCENT)

    @Test
    fun el_vector_ocupa_el_66_por_ciento_del_lado() {
        assertTrue(abs(BADGE_BRAIN_FRACTION - 0.66f) < 0.001f, "fraccion = $BADGE_BRAIN_FRACTION")
    }

    @Test
    fun el_cerebro_dibujado_mide_53_4_por_ciento_del_lado() {
        // Trazados de x = 35 a 221 (186 unidades) dentro del lienzo de 230.
        val drawn = BADGE_BRAIN_FRACTION * 186f / 230f
        assertTrue(abs(drawn - 0.534f) < 0.002f, "cerebro = $drawn del lado")
    }
}
