package org.braincards.app.domain.usecase.Flashcards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlashcardFieldsTest {

    private fun fieldsOf(question: String, answer: String): Set<CardField> =
        assertFailsWith<FlashcardFieldException> { checkFlashcardFields(question, answer) }.fields

    @Test
    fun anverso_y_reverso_con_texto_son_validos() {
        checkFlashcardFields("¿Qué significa \"to get along\"?", "Llevarse bien")
    }

    // Mutacion F3: si la excepcion llevara el campo cambiado, este test fallaria.
    @Test
    fun un_anverso_en_blanco_marca_solo_el_anverso() {
        assertEquals(setOf(CardField.Question), fieldsOf("", "Respuesta"))
    }

    // Mutacion F1: si se ignorara el reverso, este test fallaria.
    @Test
    fun un_reverso_en_blanco_marca_solo_el_reverso() {
        assertEquals(setOf(CardField.Answer), fieldsOf("Pregunta", ""))
    }

    @Test
    fun los_dos_en_blanco_marcan_los_dos() {
        assertEquals(setOf(CardField.Question, CardField.Answer), fieldsOf("", ""))
    }

    // Mutacion F2: con isEmpty en vez de isBlank, un campo de solo espacios o saltos de linea pasaria.
    @Test
    fun un_campo_de_solo_espacios_o_saltos_de_linea_cuenta_como_vacio() {
        assertEquals(setOf(CardField.Question), fieldsOf("  \n\t ", "ok"))
        assertEquals(setOf(CardField.Answer), fieldsOf("ok", "   "))
    }

    @Test
    fun el_contenido_multilinea_es_valido_y_no_se_recorta() {
        checkFlashcardFields("linea 1\nlinea 2", "  respuesta con espacios  ")
    }

    @Test
    fun la_excepcion_es_una_IllegalArgumentException() {
        assertTrue(runCatching { checkFlashcardFields("", "") }.exceptionOrNull() is IllegalArgumentException)
    }
}
