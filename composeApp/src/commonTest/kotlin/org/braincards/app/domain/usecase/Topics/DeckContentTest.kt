package org.braincards.app.domain.usecase.Topics

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckContentTest {

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    private fun topic(id: String, name: String, created: Long = 1, deleted: Boolean = false) = Topic(
        id = id, deckId = "d1", name = name, createdAt = ms(created), updatedAt = ms(created),
        deletedAt = if (deleted) ms(created + 1) else null
    )

    private fun card(
        id: String, topicId: String? = null, created: Long = 1, q: String = "Q$id", a: String = "A$id",
        deleted: Boolean = false
    ) = Flashcard(
        id = id, deckId = "d1", question = q, answer = a, createdAt = ms(created), updatedAt = ms(created),
        cardType = CardType.FLIP, topicId = topicId, deletedAt = if (deleted) ms(created + 1) else null
    )

    private fun counts(c: DeckContent) = c.topics.associate { it.topic.id to it.cardCount }

    @Test
    fun cuenta_las_tarjetas_de_cada_tema_sin_mezclarlas() {
        val c = DeckContent.of(
            listOf(topic("t1", "A"), topic("t2", "B"), topic("t3", "C")),
            listOf(card("1", "t1"), card("2", "t1"), card("3", "t2"), card("4"))
        )
        assertEquals(mapOf("t1" to 2, "t2" to 1, "t3" to 0), counts(c))
    }

    // M8: si contara tarjetas borradas este test fallaria.
    @Test
    fun las_tarjetas_borradas_no_cuentan() {
        val c = DeckContent.of(listOf(topic("t1", "A")), listOf(card("1", "t1"), card("2", "t1", deleted = true)))
        assertEquals(mapOf("t1" to 1), counts(c))
    }

    // M7: si contara por NOMBRE en vez de por id, dos temas con el mismo nombre compartirian contador.
    @Test
    fun dos_temas_con_el_mismo_nombre_llegados_por_sync_cuentan_lo_suyo_y_ambos_se_ven() {
        val c = DeckContent.of(
            listOf(topic("t1", "Verbs", created = 1), topic("t2", "verbs", created = 2)),
            listOf(card("1", "t1"), card("2", "t1"), card("3", "t2"))
        )
        assertEquals(listOf("t1", "t2"), c.topics.map { it.topic.id })
        assertEquals(mapOf("t1" to 2, "t2" to 1), counts(c))
    }

    @Test
    fun un_tema_borrado_no_aparece() {
        val c = DeckContent.of(listOf(topic("t1", "A", deleted = true), topic("t2", "B")), emptyList())
        assertEquals(listOf("t2"), c.topics.map { it.topic.id })
        assertNull(c.topic("t1"))
    }

    // M9: si el filtro de "sin tema" estuviera invertido, este test fallaria.
    @Test
    fun las_tarjetas_sin_tema_son_solo_las_que_no_tienen_topicId() {
        val c = DeckContent.of(listOf(topic("t1", "A")), listOf(card("1", "t1"), card("2"), card("3")))
        assertEquals(listOf("2", "3"), c.looseCards.map { it.id })
    }

    @Test
    fun una_tarjeta_con_topicId_colgando_va_a_sin_tema_y_no_cuenta_en_ningun_tema() {
        val c = DeckContent.of(
            listOf(topic("t1", "A"), topic("tb", "Borrado", deleted = true)),
            listOf(card("1", "t1"), card("2", "tb"), card("3", "no-existe"))
        )
        assertEquals(listOf("2", "3"), c.looseCards.map { it.id })
        assertEquals(mapOf("t1" to 1), counts(c))
    }

    @Test
    fun cardsOf_devuelve_las_de_ese_tema() {
        val c = DeckContent.of(listOf(topic("t1", "A")), listOf(card("1", "t1"), card("2"), card("3", "t1")))
        assertEquals(listOf("1", "3"), c.cardsOf("t1").map { it.id })
    }

    // M10: un orden sensible a mayusculas/acentos haria fallar este test.
    @Test
    fun los_temas_se_ordenan_por_nombre_sin_mayusculas_ni_acentos() {
        val c = DeckContent.of(
            listOf(topic("z", "zeta"), topic("b", "Banco"), topic("a", "árbol"), topic("c", "Arco")), emptyList()
        )
        assertEquals(listOf("a", "c", "b", "z"), c.topics.map { it.topic.id }) // arbol < Arco < Banco < zeta
    }

    // M11: un orden descendente haria fallar este test.
    @Test
    fun las_tarjetas_se_ordenan_por_antiguedad_y_luego_id() {
        val c = DeckContent.of(
            emptyList(), listOf(card("b", created = 5), card("c", created = 9), card("a", created = 5))
        )
        assertEquals(listOf("a", "b", "c"), c.looseCards.map { it.id })
    }

    @Test
    fun vacio_solo_si_no_hay_ni_temas_ni_tarjetas() {
        assertTrue(DeckContent.of(emptyList(), emptyList()).isEmpty)
        assertFalse(DeckContent.of(listOf(topic("t", "A")), emptyList()).isEmpty)
        assertFalse(DeckContent.of(emptyList(), listOf(card("1"))).isEmpty)
        assertFalse(DeckContent.of(listOf(topic("t", "A")), emptyList()).hasCards)
    }

    @Test
    fun la_busqueda_encuentra_temas_por_nombre_sin_acentos() {
        val c = DeckContent.of(listOf(topic("t1", "Inglés"), topic("t2", "Mates")), emptyList())
        assertEquals(listOf("t1"), c.search("ingles").topics.map { it.topic.id })
    }

    // M12: si solo buscara en el anverso, este test (coincidencia en el reverso) fallaria.
    @Test
    fun la_busqueda_encuentra_tarjetas_por_anverso_o_reverso_incluidas_las_de_un_tema() {
        val c = DeckContent.of(
            listOf(topic("t1", "Verbs")),
            listOf(
                card("1", "t1", q = "to get along", a = "llevarse bien"),
                card("2", null, q = "huge", a = "enorme"),
                card("3", null, q = "Qué", a = "Llevarse")
            )
        )
        val hits = c.search("LLEVARSE").cards
        assertEquals(listOf("1", "3"), hits.map { it.card.id })
        assertEquals("Verbs", hits.first().topicName)
        assertNull(hits.last().topicName)
    }

    @Test
    fun una_busqueda_sin_coincidencias_es_vacia() {
        val c = DeckContent.of(listOf(topic("t1", "Verbs")), listOf(card("1", "t1")))
        assertTrue(c.search("zzz").isEmpty)
    }
}
