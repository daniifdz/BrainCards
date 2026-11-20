package org.braincards.app.domain.usecase.Decks

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.SampleDeckStore
import org.braincards.app.domain.sample.SampleDeckContent
import org.braincards.app.domain.sample.SampleDeckNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateSampleDeckUseCaseTest {

    private class FakeSampleDeckStore(var failure: Throwable? = null) : SampleDeckStore {
        var decks = listOf<Deck>()
        var topics = listOf<Topic>()
        var cards = listOf<Flashcard>()
        var calls = 0

        override suspend fun insert(deck: Deck, topics: List<Topic>, cards: List<Flashcard>) {
            calls++
            failure?.let { throw it }
            this.decks = decks + deck
            this.topics = this.topics + topics
            this.cards = this.cards + cards
        }
    }

    private val now = Instant.fromEpochMilliseconds(1_000_000)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val names = SampleDeckNames("Primeros pasos", listOf("Cómo estudiar con BrainCards", "Cómo organizar y sincronizar tus mazos"))

    private val decksRepository = FakeDecksRepository()
    private val store = FakeSampleDeckStore()
    private val useCase = CreateSampleDeckUseCase(decksRepository, store, clock)

    private fun deck(name: String, deletedAt: Instant? = null, id: String = "existente") =
        Deck(id = id, name = name, createdAt = now, updatedAt = now, description = null, deletedAt = deletedAt)

    @Test
    fun crea_un_mazo_dos_temas_y_diez_tarjetas() = runTest {
        val result = useCase(names)

        assertTrue(result.created)
        assertEquals(1, store.calls)
        val deck = store.decks.single()
        assertEquals(result.deckId, deck.id)
        assertEquals("Primeros pasos", deck.name)
        assertEquals(listOf("Cómo estudiar con BrainCards", "Cómo organizar y sincronizar tus mazos"), store.topics.map { it.name })
        assertEquals(10, store.cards.size)
        assertTrue(store.topics.all { it.deckId == deck.id })
        assertTrue(store.cards.all { it.deckId == deck.id })
    }

    @Test
    fun las_cinco_primeras_tarjetas_van_al_primer_tema_y_las_otras_cinco_al_segundo() = runTest {
        useCase(names)

        val byOrder = store.cards.sortedBy { it.createdAt }
        assertEquals(List(5) { store.topics[0].id } + List(5) { store.topics[1].id }, byOrder.map { it.topicId })
        assertEquals(SampleDeckContent.topics.flatten().map { it.question }, byOrder.map { it.question })
        assertEquals(SampleDeckContent.topics.flatten().map { it.answer }, byOrder.map { it.answer })
    }

    @Test
    fun las_filas_son_como_las_de_un_alta_manual() = runTest {
        useCase(names)

        val rows = store.decks.map { Triple(it.userId, it.lastSyncedAt, it.deletedAt) } +
            store.topics.map { Triple(it.userId, it.lastSyncedAt, it.deletedAt) } +
            store.cards.map { Triple(it.userId, it.lastSyncedAt, it.deletedAt) }
        assertEquals(13, rows.size)
        assertTrue(rows.all { it == Triple(null, null, null) }, "sin dueño, sin marca de sync y sin borrar")
        assertNull(store.decks.single().description)
        assertTrue(store.cards.all { it.cardType == CardType.FLIP })
        assertTrue(store.cards.all { it.createdAt == it.updatedAt })
        assertEquals(now, store.decks.single().createdAt)
        assertEquals(now, store.decks.single().updatedAt)
    }

    @Test
    fun los_ids_son_distintos() = runTest {
        useCase(names)

        val ids = store.decks.map { it.id } + store.topics.map { it.id } + store.cards.map { it.id }
        assertEquals(13, ids.toSet().size)
    }

    @Test
    fun cada_tarjeta_es_un_milisegundo_posterior_a_la_anterior() = runTest {
        useCase(names)

        val times = store.cards.map { it.createdAt }.sorted()
        assertEquals(10, times.toSet().size, "marcas distintas")
        times.zipWithNext().forEach { (a, b) -> assertEquals(1, (b - a).inWholeMilliseconds) }
        assertEquals(now, times.first())
    }

    @Test
    fun si_el_almacen_falla_no_se_crea_nada_y_el_error_se_propaga() = runTest {
        store.failure = IllegalStateException("disco lleno")

        assertFailsWith<IllegalStateException> { useCase(names) }

        assertEquals(1, store.calls)
        assertTrue(store.decks.isEmpty() && store.topics.isEmpty() && store.cards.isEmpty())
    }

    @Test
    fun si_ya_existe_el_mazo_no_se_duplica_y_se_devuelve_el_existente() = runTest {
        decksRepository.addDeck(deck("Primeros pasos"))

        val result = useCase(names)

        assertFalse(result.created)
        assertEquals("existente", result.deckId)
        assertEquals(0, store.calls, "no se escribe nada")
    }

    @Test
    fun una_segunda_llamada_tras_crearlo_tampoco_lo_duplica() = runTest {
        val first = useCase(names)
        decksRepository.addDeck(store.decks.single())

        val second = useCase(names)

        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(first.deckId, second.deckId)
        assertEquals(1, store.calls)
    }

    @Test
    fun un_mazo_borrado_con_ese_nombre_no_cuenta_como_existente() = runTest {
        decksRepository.addDeck(deck("Primeros pasos", deletedAt = now))

        val result = useCase(names)

        assertTrue(result.created)
        assertEquals(1, store.calls)
    }

    @Test
    fun otro_mazo_con_nombre_parecido_no_es_el_de_ejemplo_y_no_se_crea_nada() = runTest {
        decksRepository.addDeck(deck("primeros pasos"))
        decksRepository.addDeck(deck("Primeros pasos 2", id = "otro"))

        assertEquals(SampleDeckResult.AlreadyHasDecks, useCase(names))
        assertEquals(0, store.calls)
    }

    @Test
    fun con_otro_mazo_vivo_y_sin_el_de_ejemplo_no_se_escribe_nada() = runTest {
        decksRepository.addDeck(deck("Inglés B2", id = "suyo"))

        val result = useCase(names)

        assertEquals(SampleDeckResult.AlreadyHasDecks, result)
        assertEquals(0, store.calls, "no se escribe nada")
        assertTrue(store.decks.isEmpty() && store.topics.isEmpty() && store.cards.isEmpty())
    }

    @Test
    fun con_varios_mazos_vivos_tampoco_se_escribe_nada() = runTest {
        decksRepository.addDeck(deck("Uno", id = "1"))
        decksRepository.addDeck(deck("Dos", id = "2"))

        assertEquals(SampleDeckResult.AlreadyHasDecks, useCase(names))
        assertEquals(0, store.calls)
    }

    @Test
    fun un_mazo_borrado_de_otro_nombre_no_cuenta_como_mazo_vivo_y_se_crea() = runTest {
        decksRepository.addDeck(deck("Inglés B2", deletedAt = now, id = "borrado"))

        val result = useCase(names)

        assertTrue(result is SampleDeckResult.Created)
        assertEquals(1, store.calls)
    }

    @Test
    fun si_existe_el_de_ejemplo_junto_a_otros_mazos_gana_el_existente() = runTest {
        decksRepository.addDeck(deck("Inglés B2", id = "suyo"))
        decksRepository.addDeck(deck("Primeros pasos", id = "ejemplo"))

        assertEquals(SampleDeckResult.AlreadyExists("ejemplo"), useCase(names))
        assertEquals(0, store.calls)
    }

    @Test
    fun exige_un_nombre_por_cada_tema() = runTest {
        assertFailsWith<IllegalArgumentException> { useCase(SampleDeckNames("Primeros pasos", listOf("solo uno"))) }
        assertEquals(0, store.calls)
    }
}
