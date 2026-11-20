package org.braincards.app.domain.sample

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleDeckContentTest {

    private val all = SampleDeckContent.topics.flatten()

    @Test
    fun son_dos_temas_de_cinco_tarjetas() {
        assertEquals(2, SampleDeckContent.topics.size)
        assertEquals(listOf(5, 5), SampleDeckContent.topics.map { it.size })
        assertEquals(10, SampleDeckContent.cardCount)
        assertEquals(10, all.size)
    }

    @Test
    fun ninguna_pregunta_ni_respuesta_esta_vacia() {
        assertTrue(all.all { it.question.isNotBlank() && it.answer.isNotBlank() })
    }

    @Test
    fun no_hay_preguntas_ni_respuestas_duplicadas() {
        assertEquals(10, all.map { it.question.trim() }.toSet().size)
        assertEquals(10, all.map { it.answer.trim() }.toSet().size)
    }

    @Test
    fun la_primera_tarjeta_pregunta_por_la_repeticion_espaciada() {
        assertEquals("¿Qué es la repetición espaciada?", all.first().question)
    }

    @Test
    fun la_ultima_tarjeta_explica_que_el_mazo_se_puede_borrar() {
        val last = all.last()
        assertEquals("Este mazo de ejemplo, ¿lo puedo borrar?", last.question)
        assertEquals("Sí. Puedes borrarlo cuando quieras desde tu lista de mazos.", last.answer)
    }

    @Test
    fun las_tarjetas_de_repaso_libre_y_de_cuenta_estan_en_su_tema() {
        assertTrue(SampleDeckContent.topics[0].last().answer.startsWith("Sí, con Repaso libre."))
        assertTrue(SampleDeckContent.topics[1].any { it.question == "¿Puedo usar BrainCards sin crear una cuenta?" })
    }
}
