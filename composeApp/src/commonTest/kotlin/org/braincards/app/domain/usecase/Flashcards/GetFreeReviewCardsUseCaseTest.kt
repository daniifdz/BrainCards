package org.braincards.app.domain.usecase.Flashcards

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeFreeReviewCardsSource
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.StudyScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Cola del repaso libre: todas las tarjetas del origen (no solo las pendientes), base ordenada por id y barajado con semilla. */
class GetFreeReviewCardsUseCaseTest {

    private val t0 = Instant.fromEpochMilliseconds(1_000)

    private fun card(id: String, deckId: String = "d1") = Flashcard(
        id = id, deckId = deckId, question = "Q$id", answer = "A$id", createdAt = t0, updatedAt = t0, cardType = CardType.FLIP
    )

    private val identity = CardShuffler { cards, _ -> cards }

    @Test
    fun devuelve_TODAS_las_tarjetas_del_origen_no_solo_las_pendientes() = runTest {
        val source = FakeFreeReviewCardsSource(listOf(card("a"), card("b"), card("c"), card("d"), card("e")))
        val result = GetFreeReviewCardsUseCase(source, identity)(StudyScope.All, seed = 1)
        assertEquals(listOf("a", "b", "c", "d", "e"), result.map { it.id })
    }

    @Test
    fun el_ambito_todos_lee_getAll_y_el_de_un_mazo_lee_getByDeck() = runTest {
        val source = FakeFreeReviewCardsSource(listOf(card("a", "d1"), card("b", "d2"), card("c", "d1")))
        val useCase = GetFreeReviewCardsUseCase(source, identity)

        assertEquals(listOf("a", "b", "c"), useCase(StudyScope.All, 1).map { it.id })
        assertEquals(1, source.getAllCalls)
        assertTrue(source.getByDeckCalls.isEmpty())

        assertEquals(listOf("a", "c"), useCase(StudyScope.Deck("d1"), 1).map { it.id })
        assertEquals(listOf("d1"), source.getByDeckCalls)
        assertEquals(1, source.getAllCalls, "el ambito de un mazo no lee todos los mazos")
    }

    @Test
    fun el_barajador_recibe_la_base_ORDENADA_por_id_y_la_semilla() = runTest {
        val seen = mutableListOf<Pair<List<String>, Long>>()
        val spy = CardShuffler { cards, seed ->
            seen += cards.map { it.id } to seed
            cards
        }
        val source = FakeFreeReviewCardsSource(listOf(card("c"), card("a"), card("b")))
        GetFreeReviewCardsUseCase(source, spy)(StudyScope.All, seed = 42L)
        assertEquals(listOf(listOf("a", "b", "c") to 42L), seen)
    }

    @Test
    fun se_usa_el_resultado_del_barajador_inyectado() = runTest {
        val reverse = CardShuffler { cards, _ -> cards.reversed() }
        val source = FakeFreeReviewCardsSource(listOf(card("a"), card("b"), card("c")))
        assertEquals(listOf("c", "b", "a"), GetFreeReviewCardsUseCase(source, reverse)(StudyScope.All, 1).map { it.id })
    }

    // ---- barajado real con semilla ----

    private val many = (1..30).map { card("c%02d".format(it)) }

    @Test
    fun la_misma_semilla_da_el_mismo_orden_y_es_una_permutacion() {
        val a = SeededCardShuffler.shuffle(many, 123L)
        val b = SeededCardShuffler.shuffle(many, 123L)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(many.map { it.id }.sorted(), a.map { it.id }.sorted(), "no pierde ni duplica tarjetas")
    }

    @Test
    fun semillas_distintas_dan_ordenes_distintos_y_distintos_del_original() {
        val a = SeededCardShuffler.shuffle(many, 1L).map { it.id }
        val b = SeededCardShuffler.shuffle(many, 2L).map { it.id }
        assertNotEquals(a, b)
        assertNotEquals(many.map { it.id }, a, "realmente baraja")
    }

    @Test
    fun el_orden_final_no_depende_del_orden_en_que_la_fuente_devuelva_las_tarjetas() = runTest {
        val forward = GetFreeReviewCardsUseCase(FakeFreeReviewCardsSource(many), SeededCardShuffler)(StudyScope.All, 9L)
        val backward = GetFreeReviewCardsUseCase(FakeFreeReviewCardsSource(many.reversed()), SeededCardShuffler)(StudyScope.All, 9L)
        assertEquals(forward.map { it.id }, backward.map { it.id })
    }

    @Test
    fun sin_tarjetas_devuelve_lista_vacia() = runTest {
        assertTrue(GetFreeReviewCardsUseCase(FakeFreeReviewCardsSource(), SeededCardShuffler)(StudyScope.All, 1L).isEmpty())
    }
}
