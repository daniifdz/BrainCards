package org.braincards.app.presentation.screens.studyStats

import org.braincards.app.domain.models.StudyStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatsStateTest {

    private val stats = StudyStats(newCards = 73, learningCards = 64, matureCards = 47)

    @Test
    fun sin_mazos_es_Empty_aunque_haya_datos_de_madurez_desfasados() {
        assertEquals(StatsUiState.Empty, selectStatsState(deckCount = 0, stats = stats, dueCount = 5))
        assertEquals(StatsUiState.Empty, selectStatsState(deckCount = 0, stats = StudyStats(0, 0, 0), dueCount = 0))
    }

    @Test
    fun con_mazos_y_tarjetas_es_Content_con_el_total_como_suma() {
        val s = selectStatsState(deckCount = 3, stats = stats, dueCount = 12) as StatsUiState.Content
        assertEquals(3, s.deckCount)
        assertEquals(12, s.dueCount)
        assertEquals(184, s.totalCards)
        assertTrue(s.hasCards)
    }

    @Test
    fun con_mazos_pero_sin_tarjetas_es_Content_0_0_0_sin_donut() {
        val s = selectStatsState(deckCount = 2, stats = StudyStats(0, 0, 0), dueCount = 0) as StatsUiState.Content
        assertEquals(0, s.totalCards)
        assertFalse(s.hasCards)
    }

    @Test
    fun el_total_del_modelo_es_la_suma_de_las_tres_categorias() {
        assertEquals(6, StudyStats(1, 2, 3).totalCards)
    }

    @Test
    fun el_ejemplo_73_64_47_da_40_35_25() {
        assertEquals(listOf(40, 35, 25), stats.percentages())
    }

    @Test
    fun los_porcentajes_siempre_suman_100_cuando_hay_tarjetas() {
        for (n in 0..40) for (l in 0..40) for (m in 0..40) {
            if (n + l + m == 0) continue
            assertEquals(100, maturityPercentages(listOf(n, l, m)).sum(), "n=$n l=$l m=$m")
        }
    }

    @Test
    fun con_total_cero_todo_es_cero_sin_dividir() {
        assertEquals(listOf(0, 0, 0), maturityPercentages(listOf(0, 0, 0)))
    }

    @Test
    fun una_sola_categoria_es_el_100_por_cien() {
        assertEquals(listOf(0, 100, 0), maturityPercentages(listOf(0, 7, 0)))
    }

    @Test
    fun un_empate_de_restos_da_el_punto_extra_a_la_primera() {
        // 1/3 cada una: suelo 33 y resto igual -> el punto que falta va a la primera.
        assertEquals(listOf(34, 33, 33), maturityPercentages(listOf(1, 1, 1)))
    }

    @Test
    fun una_categoria_minuscula_no_pasa_a_cero_si_su_resto_es_el_mayor() {
        // 1/201 = 0,49 %: suelo 0; las otras dos se quedan en 49 y 50 -> el punto extra va al mayor resto.
        val p = maturityPercentages(listOf(1, 100, 100))
        assertEquals(100, p.sum())
    }
}
