package org.braincards.app.domain.models

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.ReviewQuality.AGAIN
import org.braincards.app.domain.models.ReviewQuality.EASY
import org.braincards.app.domain.models.ReviewQuality.GOOD
import org.braincards.app.domain.models.ReviewQuality.HARD
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyQueueTest {

    private fun card(id: String) = Flashcard(
        id = id, deckId = "d1", question = "Q$id", answer = "A$id",
        createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1),
        cardType = CardType.FLIP
    )

    private fun queueOf(vararg ids: String) = StudyQueue(queue = ids.map { card(it) })

    private fun StudyQueue.rate(quality: ReviewQuality) = submitResult(currentCard!!, quality)

    private fun StudyQueue.ids() = queue.map { it.id }

    @Test
    fun el_total_y_los_restantes_arrancan_en_el_numero_de_tarjetas() {
        val q = queueOf("a", "b", "c")
        assertEquals(3, q.total)
        assertEquals(3, q.remaining)
        assertEquals("a", q.currentCard?.id)
        assertEquals(0, q.totalReviews)
        assertEquals(0f, q.progress)
    }

    @Test
    fun un_acierto_saca_la_tarjeta_baja_N_y_deja_M_fijo() {
        val q = queueOf("a", "b", "c").rate(GOOD)
        assertEquals(listOf("b", "c"), q.ids())
        assertEquals(2, q.remaining)
        assertEquals(3, q.total)
    }

    @Test
    fun otra_vez_mueve_la_tarjeta_al_final_sin_duplicarla_y_N_y_M_no_cambian() {
        val q = queueOf("a", "b", "c").rate(AGAIN)
        assertEquals(listOf("b", "c", "a"), q.ids())
        assertEquals(3, q.remaining)
        assertEquals(3, q.total)
    }

    @Test
    fun una_unica_tarjeta_fallada_reaparece_enseguida() {
        val q = queueOf("a").rate(AGAIN)
        assertEquals("a", q.currentCard?.id)
        assertEquals(1, q.remaining)
        assertTrue(!q.isFinished)
    }

    @Test
    fun la_sesion_solo_termina_cuando_todas_han_salido_con_un_acierto() {
        var q = queueOf("a", "b")
        q = q.rate(AGAIN)
        q = q.rate(GOOD)
        assertTrue(!q.isFinished)
        q = q.rate(EASY)
        assertTrue(q.isFinished)
    }

    @Test
    fun cada_calidad_cuenta_en_su_casilla_y_el_total_es_la_suma() {
        var q = queueOf("a", "b", "c", "d")
        q = q.rate(AGAIN).rate(HARD).rate(GOOD).rate(EASY) // a(Again) b c d
        assertEquals(1, q.results().countOf(AGAIN))
        assertEquals(1, q.results().countOf(HARD))
        assertEquals(1, q.results().countOf(GOOD))
        assertEquals(1, q.results().countOf(EASY))
        assertEquals(4, q.totalReviews)
        assertEquals(4, q.results().totalReviews)
    }

    @Test
    fun el_total_son_pulsaciones_y_puede_superar_el_total_de_tarjetas() {
        val q = queueOf("a").rate(AGAIN).rate(AGAIN).rate(GOOD)
        assertEquals(3, q.totalReviews)
        assertEquals(1, q.total)
        assertEquals(2, q.results().countOf(AGAIN))
        assertEquals(1, q.results().countOf(GOOD))
    }

    @Test
    fun el_progreso_avanza_al_salir_una_tarjeta_y_una_fallada_no_lo_hace_avanzar() {
        val q = queueOf("a", "b", "c", "d")
        assertEquals(0.25f, q.rate(GOOD).progress)
        assertEquals(0f, q.rate(AGAIN).progress)
    }

    @Test
    fun saltar_una_tarjeta_baja_N_y_M_y_mantiene_N_menor_o_igual_que_M() {
        val q = queueOf("a", "b", "c").skipCurrent()
        assertEquals(listOf("b", "c"), q.ids())
        assertEquals(2, q.remaining)
        assertEquals(2, q.total)
        assertTrue(q.remaining <= q.total)
        assertEquals(0f, q.progress)
        assertEquals(0, q.totalReviews) // saltar no puntua
    }

    @Test
    fun saltar_la_ultima_tras_puntuar_deja_la_cola_terminada_con_sus_contadores() {
        val q = queueOf("a", "b").rate(GOOD).skipCurrent()
        assertTrue(q.isFinished)
        assertEquals(1, q.total)
        assertEquals(1, q.totalReviews)
        assertNull(q.currentCard)
    }

    @Test
    fun el_total_nunca_baja_de_cero() {
        val q = StudyQueue(queue = emptyList()).skipCurrent()
        assertEquals(0, q.total)
        assertEquals(0f, q.progress)
    }
}
