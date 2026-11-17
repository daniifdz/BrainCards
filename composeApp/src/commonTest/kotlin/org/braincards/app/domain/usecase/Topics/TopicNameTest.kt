package org.braincards.app.domain.usecase.Topics

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Topic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TopicNameTest {

    private fun t(
        id: String, name: String, deckId: String = "d1", created: Long = 1,
        deleted: Boolean = false
    ) = Topic(
        id = id, deckId = deckId, name = name,
        createdAt = Instant.fromEpochMilliseconds(created), updatedAt = Instant.fromEpochMilliseconds(created),
        deletedAt = if (deleted) Instant.fromEpochMilliseconds(created + 1) else null
    )

    private fun reasonOf(block: () -> Unit): TopicNameError =
        assertFailsWith<TopicNameException> { block() }.reason

    @Test
    fun un_nombre_vacio_o_en_blanco_es_Blank() {
        assertEquals(TopicNameError.Blank, reasonOf { checkTopicName("", "d1", emptyList()) })
        assertEquals(TopicNameError.Blank, reasonOf { checkTopicName("   ", "d1", emptyList()) })
    }

    @Test
    fun la_excepcion_es_una_IllegalArgumentException() {
        assertTrue(runCatching { checkTopicName("", "d1", emptyList()) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun un_nombre_libre_es_valido() {
        checkTopicName("Verbs", "d1", listOf(t("a", "Nouns")))
    }

    @Test
    fun el_mismo_nombre_exacto_es_Duplicate() {
        assertEquals(TopicNameError.Duplicate, reasonOf { checkTopicName("Verbs", "d1", listOf(t("a", "Verbs"))) })
    }

    // M4a: si la clave no pasara a minusculas, este test fallaria.
    @Test
    fun no_distingue_mayusculas() {
        assertEquals(
            TopicNameError.Duplicate,
            reasonOf { checkTopicName("phrasal VERBS", "d1", listOf(t("a", "Phrasal Verbs"))) }
        )
    }

    // M4b: si la clave no colapsara los espacios (\s+), este test fallaria.
    @Test
    fun colapsa_los_espacios_internos() {
        assertEquals(
            TopicNameError.Duplicate,
            reasonOf { checkTopicName("Phrasal     verbs", "d1", listOf(t("a", "Phrasal verbs"))) }
        )
    }

    @Test
    fun ignora_los_espacios_de_los_extremos() {
        assertEquals(
            TopicNameError.Duplicate,
            reasonOf { checkTopicName("  Phrasal verbs ", "d1", listOf(t("a", "Phrasal verbs"))) }
        )
    }

    @Test
    fun los_acentos_si_distinguen() {
        checkTopicName("Fisica", "d1", listOf(t("a", "Física")))
    }

    // M1: sin el filtro deletedAt == null este test fallaria.
    @Test
    fun un_tema_borrado_deja_su_nombre_libre() {
        checkTopicName("Verbs", "d1", listOf(t("a", "Verbs", deleted = true)))
    }

    // M3: sin el filtro por deckId este test fallaria.
    @Test
    fun el_mismo_nombre_en_otro_mazo_es_valido() {
        checkTopicName("Verbs", "d1", listOf(t("a", "Verbs", deckId = "d2")))
    }

    // M2: sin la exclusion del propio id este test fallaria.
    @Test
    fun al_renombrar_se_excluye_el_propio_tema() {
        checkTopicName("phrasal", "d1", listOf(t("a", "Phrasal")), ownId = "a")
    }

    @Test
    fun al_renombrar_sigue_chocando_con_otro_tema() {
        assertEquals(
            TopicNameError.Duplicate,
            reasonOf { checkTopicName("Nouns", "d1", listOf(t("a", "Phrasal"), t("b", "Nouns")), ownId = "a") }
        )
    }

    @Test
    fun el_orden_ignora_mayusculas_y_acentos_y_desempata_por_antiguedad_e_id() {
        val sorted = listOf(
            t("z", "zeta"), t("b2", "Árbol", created = 5), t("b1", "arbol", created = 3),
            t("a", "Banco"), t("c", "ánimo")
        ).sortedWith(topicComparator).map { it.id }
        // "ánimo" < "arbol"(=Árbol) < "Banco" < "zeta"; los dos "arbol" se desempatan por antiguedad.
        assertEquals(listOf("c", "b1", "b2", "a", "z"), sorted)
    }

    @Test
    fun la_busqueda_ignora_mayusculas_y_acentos() {
        assertTrue(topicMatchesQuery("Inglés B2", "ingles"))
        assertTrue(topicMatchesQuery("Inglés B2", ""))
        assertFalse(topicMatchesQuery("Inglés B2", "frances"))
    }
}
