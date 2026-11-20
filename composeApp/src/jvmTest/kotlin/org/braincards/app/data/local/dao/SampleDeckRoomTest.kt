package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.DeckSummariesObserverImpl
import org.braincards.app.data.repository.FlashcardRepositoryImpl
import org.braincards.app.data.repository.SampleDeckStoreImpl
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.sample.SampleDeckNames
import org.braincards.app.domain.usecase.Decks.CreateSampleDeckUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El mazo de ejemplo contra una Room real en memoria (foreign_keys = ON) y los repositorios de siempre: se guarda en UNA
 * transacción (si falla una tarjeta no queda nada), con las mismas filas que el alta manual (sin dueño ni marca de sync: las
 * adopta y sube el sync), sin programación (todas «nuevas» y pendientes) y como un mazo normal para el repaso libre.
 */
class SampleDeckRoomTest {

    private lateinit var db: AppDatabase

    private val names = SampleDeckNames("Primeros pasos", listOf("Cómo estudiar con BrainCards", "Cómo organizar y sincronizar tus mazos"))
    private val now = Instant.fromEpochMilliseconds(5_000_000)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun decksRepository(flashcardDao: FlashcardDao = db.flashcardDao()) =
        DeckRepositoryImpl(db.deckDao(), db.topicDao(), flashcardDao, RoomTransactionRunner(db))

    private fun topicsRepository(flashcardDao: FlashcardDao = db.flashcardDao()) =
        TopicRepositoryImpl(db.topicDao(), flashcardDao, RoomTransactionRunner(db))

    private fun useCase(flashcardDao: FlashcardDao = db.flashcardDao()): CreateSampleDeckUseCase {
        val store = SampleDeckStoreImpl(
            transactionRunner = RoomTransactionRunner(db),
            decksRepository = decksRepository(flashcardDao),
            topicsRepository = topicsRepository(flashcardDao),
            flashcardRepository = FlashcardRepositoryImpl(flashcardDao)
        )
        return CreateSampleDeckUseCase(decksRepository(flashcardDao), store, clock)
    }

    /** FlashcardDao real, pero la inserción número [n] de una tarjeta falla (las anteriores ya se han ejecutado). */
    private fun failingOnCardInsert(n: Int): FlashcardDao {
        val real = db.flashcardDao()
        return object : FlashcardDao by real {
            private var inserted = 0
            override suspend fun addFlashcard(flashcard: FlashcardEntity) {
                if (++inserted == n) throw IllegalStateException("fallo simulado en la tarjeta $n")
                real.addFlashcard(flashcard)
            }
        }
    }

    private suspend fun cards(deckId: String) = db.flashcardDao().getFlashcardsByDeckId(deckId).first()
    private suspend fun topics(deckId: String) = db.topicDao().getTopicsByDeckId(deckId).first()

    @Test
    fun guarda_un_mazo_dos_temas_y_diez_tarjetas() = runBlocking {
        val result = useCase()(names)

        assertTrue(result.created)
        val deck = db.deckDao().getDeckByIdIncludingDeleted(result.deckId)!!
        assertEquals("Primeros pasos", deck.name)
        assertEquals(2, topics(result.deckId).size)
        val cards = cards(result.deckId)
        assertEquals(10, cards.size)
        assertEquals(setOf(5), cards.groupingBy { it.topicId }.eachCount().values.toSet())
    }

    @Test
    fun todas_las_filas_son_como_un_alta_manual_sin_dueno_ni_marca_de_sync() = runBlocking {
        val id = useCase()(names).deckId

        val deck = db.deckDao().getDeckByIdIncludingDeleted(id)!!
        assertNull(deck.userId)
        assertNull(deck.lastSyncedAt)
        assertNull(deck.deletedAt)
        topics(id).forEach { assertNull(it.userId); assertNull(it.lastSyncedAt); assertNull(it.deletedAt) }
        cards(id).forEach { assertNull(it.userId); assertNull(it.lastSyncedAt); assertNull(it.deletedAt) }
    }

    @Test
    fun tras_iniciar_sesion_todo_queda_pendiente_de_subir() = runBlocking {
        val decks = decksRepository()
        val topics = topicsRepository()
        val flashcards = FlashcardRepositoryImpl(db.flashcardDao())
        useCase()(names)

        assertEquals(1, decks.claimOrphanedDecks("u"))
        assertEquals(2, topics.claimOrphanedTopics("u"))
        assertEquals(10, flashcards.claimOrphanedFlashcards("u"))

        assertEquals(1, decks.getPendingSync("u").size)
        assertEquals(2, topics.getPendingSync("u").size)
        assertEquals(10, flashcards.getPendingSync("u").size)
    }

    @Test
    fun las_tarjetas_no_tienen_programacion_y_salen_pendientes() = runBlocking {
        val id = useCase()(names).deckId

        assertTrue(db.cardSchedulingDao().getAll().first().isEmpty(), "ninguna fila de card_scheduling")
        assertEquals(10, db.flashcardDao().observeDueCount(now).first())
        assertEquals(10, db.flashcardDao().getAllDueFlashcards(now).size)
        assertEquals(10, db.flashcardDao().getDueFlashcardsByDeck(id, now).size)
    }

    @Test
    fun el_mazo_cuenta_diez_tarjetas_en_la_lista() = runBlocking {
        val id = useCase()(names).deckId

        val summary = DeckSummariesObserverImpl(db.deckDao()).observeDeckSummaries().first().single()
        assertEquals(id, summary.deck.id)
        assertEquals(10, summary.cardCount)
    }

    @Test
    fun las_tarjetas_se_muestran_en_el_orden_del_contenido() = runBlocking {
        val id = useCase()(names).deckId

        val ordered = cards(id).sortedWith(compareBy({ it.createdAt }, { it.id }))
        assertEquals("¿Qué es la repetición espaciada?", ordered.first().question)
        assertEquals("Este mazo de ejemplo, ¿lo puedo borrar?", ordered.last().question)
    }

    @Test
    fun el_repaso_libre_lo_trata_como_un_mazo_normal() = runBlocking {
        val id = useCase()(names).deckId

        assertEquals(10, db.flashcardDao().getFreeReviewCardsByDeck(id).size)
        assertEquals(10, db.flashcardDao().getFreeReviewCards().size)
    }

    @Test
    fun si_falla_una_tarjeta_a_mitad_no_queda_nada() {
        runBlocking {
            assertFailsWith<IllegalStateException> { useCase(failingOnCardInsert(6))(names) }

            assertTrue(db.deckDao().observeDeckSummaries().first().isEmpty(), "ni mazo")
            assertEquals(0, topicsRepository().claimOrphanedTopics("u"), "ni temas")
            assertEquals(0, decksRepository().claimOrphanedDecks("u"), "ni filas de mazo huérfanas")
            assertEquals(0, db.flashcardDao().getFreeReviewCards().size, "ni tarjetas")
            assertEquals(0, db.flashcardDao().observeDueCount(now).first())
        }
    }

    @Test
    fun tras_un_fallo_se_puede_volver_a_crear_sin_duplicados() = runBlocking {
        assertFailsWith<IllegalStateException> { useCase(failingOnCardInsert(10))(names) }

        val ok = useCase()(names)

        assertTrue(ok.created)
        assertEquals(1, db.deckDao().observeDeckSummaries().first().size)
        assertEquals(10, cards(ok.deckId).size)
    }

    @Test
    fun una_segunda_llamada_con_el_mazo_ya_creado_no_duplica() = runBlocking {
        val first = useCase()(names)
        val second = useCase()(names)

        assertTrue(first.created)
        assertEquals(false, second.created)
        assertEquals(first.deckId, second.deckId)
        assertEquals(1, db.deckDao().observeDeckSummaries().first().size)
        assertEquals(10, db.flashcardDao().getFreeReviewCards().size)
    }

    @Test
    fun un_mazo_con_ese_nombre_borrado_no_impide_crear_uno_nuevo() = runBlocking {
        val first = useCase()(names)
        decksRepository().deleteDeck(db.deckDao().getDeckByIdIncludingDeleted(first.deckId)!!.let {
            org.braincards.app.domain.models.Deck(it.id, it.name, it.createdAt, it.updatedAt, it.description)
        })

        val second = useCase()(names)

        assertTrue(second.created)
        assertTrue(second.deckId != first.deckId)
        assertEquals(1, db.deckDao().observeDeckSummaries().first().size, "solo el nuevo está vivo")
    }
}
