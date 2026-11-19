package org.braincards.app.domain.models

import org.braincards.app.domain.models.ReviewQuality.AGAIN
import org.braincards.app.domain.models.ReviewQuality.EASY
import org.braincards.app.domain.models.ReviewQuality.GOOD
import org.braincards.app.domain.models.ReviewQuality.HARD
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyResultsModelTest {

    @Test
    fun el_total_es_la_suma_de_las_4_casillas() {
        val r = StudyResults(mapOf(AGAIN to 2, HARD to 3, GOOD to 5, EASY to 2))
        assertEquals(12, r.totalReviews)
        assertEquals(2, r.countOf(AGAIN))
        assertEquals(3, r.countOf(HARD))
        assertEquals(5, r.countOf(GOOD))
        assertEquals(2, r.countOf(EASY))
        assertEquals(r.totalReviews, listOf(AGAIN, HARD, GOOD, EASY).sumOf { r.countOf(it) })
    }

    @Test
    fun una_calidad_sin_pulsaciones_cuenta_cero_y_no_resta() {
        val r = StudyResults(mapOf(GOOD to 1))
        assertEquals(0, r.countOf(AGAIN))
        assertEquals(1, r.totalReviews)
    }

    @Test
    fun una_sesion_con_un_solo_repaso_totaliza_uno() {
        assertEquals(1, StudyResults(mapOf(EASY to 1)).totalReviews)
    }
}
