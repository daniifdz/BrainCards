package org.braincards.app.presentation.screens.study

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Decisiones puras de la UI del repaso libre en Estudio y Resultados. */
class FreeReviewUiTest {

    @Test
    fun el_umbral_del_aviso_es_600_dp() = assertEquals(600f, FREE_REVIEW_NOTICE_MIN_WIDTH_DP)

    @Test
    fun el_aviso_aparece_desde_600_dp_y_no_por_debajo() {
        assertFalse(showFreeReviewNotice(359.9f), "movil estrecho")
        assertFalse(showFreeReviewNotice(360f), "movil")
        assertFalse(showFreeReviewNotice(599.9f), "justo por debajo")
        assertTrue(showFreeReviewNotice(600f), "justo en el umbral")
        assertTrue(showFreeReviewNotice(601f))
        assertTrue(showFreeReviewNotice(1280f), "Desktop")
    }

    @Test
    fun el_chip_sale_solo_en_el_repaso_libre() {
        assertTrue(showFreeReviewChip(freeReview = true))
        assertFalse(showFreeReviewChip(freeReview = false), "la sesion normal NO lleva chip")
    }

    @Test
    fun el_aviso_de_Resultados_sale_solo_en_el_repaso_libre() {
        assertTrue(showResultsFreeNotice(freeReview = true))
        assertFalse(showResultsFreeNotice(freeReview = false))
    }

    @Test
    fun el_titulo_de_Resultados_es_propio_en_el_repaso_libre() {
        assertEquals("study_results_title_free", studyResultsTitleRes(freeReview = true).key)
        assertEquals("study_results_title", studyResultsTitleRes(freeReview = false).key, "la sesion normal sigue con «¡Sesion completada!»")
    }

    @Test
    fun los_estados_de_estudio_son_sesion_normal_por_defecto() {
        val results = org.braincards.app.domain.models.StudyResults(emptyMap())
        assertFalse(StudyState.Finished(results, label = null).freeReview)
    }
}
