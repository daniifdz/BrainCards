package org.braincards.app.domain.usecase.Decks

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.DeckSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun deck(id: String, name: String, createdAt: Long = 0, deleted: Boolean = false) = Deck(
    id = id,
    name = name,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(createdAt),
    description = null,
    deletedAt = if (deleted) Instant.fromEpochMilliseconds(createdAt + 1) else null
)

class DeckNameTest {

    @Test
    fun normalizar_recorta_colapsa_espacios_y_pasa_a_minusculas() {
        assertEquals("vocabulario inglés b2", normalizeDeckName("  Vocabulario   INGLÉS \t B2 "))
    }

    @Test
    fun los_acentos_SI_distinguen_para_el_duplicado() {
        assertNotEquals(normalizeDeckName("Física"), normalizeDeckName("Fisica"))
    }

    @Test
    fun la_clave_de_orden_pliega_acentos_y_la_enie() {
        assertEquals("ingles b2", deckSortKey("Inglés  B2"))
        assertEquals("ano", deckSortKey("Año"))
    }

    @Test
    fun la_busqueda_no_distingue_mayusculas_ni_acentos_y_en_blanco_coincide_con_todo() {
        assertTrue(deckMatchesQuery("Vocabulario Inglés", "INGLES"))
        assertTrue(deckMatchesQuery("Vocabulario Inglés", "  "))
        assertFalse(deckMatchesQuery("Vocabulario Inglés", "frances"))
    }

    private fun summary(id: String, name: String, createdAt: Long = 0) = DeckSummary(deck(id, name, createdAt), 0)

    @Test
    fun el_orden_es_por_nombre_sin_mayusculas_y_con_acentos_plegados() {
        val sorted = listOf(
            summary("1", "zoología"),
            summary("2", "Álgebra"),
            summary("3", "Biología"),
            summary("4", "algoritmos")
        ).sortedWith(deckSummaryComparator).map { it.deck.name }
        // "Álgebra" y "algoritmos" van antes que "Biología" (por codepoint, Á iria despues de z).
        assertEquals(listOf("Álgebra", "algoritmos", "Biología", "zoología"), sorted)
    }

    @Test
    fun con_nombres_iguales_desempata_por_antiguedad_y_luego_por_id() {
        val sorted = listOf(
            summary("b", "Mazo", createdAt = 5),
            summary("z", "mazo", createdAt = 1),
            summary("a", "MAZO", createdAt = 5)
        ).sortedWith(deckSummaryComparator).map { it.deck.id }
        assertEquals(listOf("z", "a", "b"), sorted)
    }

    @Test
    fun un_nombre_en_blanco_es_Blank() {
        val e = assertFailsWith<DeckNameException> { checkDeckName("   ", existing = emptyList()) }
        assertEquals(DeckNameError.Blank, e.reason)
    }

    @Test
    fun un_duplicado_sin_distinguir_mayusculas_ni_espacios_es_Duplicate() {
        val existing = listOf(deck("1", "Vocabulario Inglés"))
        listOf("vocabulario inglés", "  VOCABULARIO   INGLÉS  ").forEach { name ->
            val e = assertFailsWith<DeckNameException> { checkDeckName(name, existing) }
            assertEquals(DeckNameError.Duplicate, e.reason, name)
        }
    }

    @Test
    fun el_mismo_nombre_sin_acento_NO_es_duplicado() {
        checkDeckName("Fisica", existing = listOf(deck("1", "Física")))
    }

    @Test
    fun al_renombrar_se_excluye_el_propio_mazo_pero_no_los_demas() {
        val existing = listOf(deck("1", "Uno"), deck("2", "Dos"))
        checkDeckName("uno", existing, ownId = "1") // el propio: permitido
        val e = assertFailsWith<DeckNameException> { checkDeckName("uno", existing, ownId = "2") }
        assertEquals(DeckNameError.Duplicate, e.reason)
    }

    @Test
    fun el_nombre_de_un_mazo_borrado_queda_libre() {
        checkDeckName("Uno", existing = listOf(deck("1", "Uno", deleted = true)))
    }
}
