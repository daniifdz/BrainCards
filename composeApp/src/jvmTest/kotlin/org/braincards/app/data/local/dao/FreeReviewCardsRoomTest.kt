package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.local.entity.TopicEntity
import org.braincards.app.data.repository.FreeReviewCardsSourceImpl
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.domain.usecase.Flashcards.GetFreeReviewCardsUseCase
import org.braincards.app.domain.usecase.Flashcards.SeededCardShuffler
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consulta del REPASO LIBRE contra una Room real: TODAS las tarjetas activas (viva Y de un mazo vivo: LIVE_DECK_JOIN), SIN filtrar por
 * pendientes, ordenadas por id. Mismo criterio de «activa» que LiveDeckRoomTest.
 *
 * Siembra: mazo A vivo (a1 sin scheduling, a2 vencida, a3 FUTURA, a4 borrada, a5 con un tema BORRADO), mazo B en TOMBSTONE con tarjetas
 * VIVAS (b1, b2) y mazo C vivo (c1).
 */
class FreeReviewCardsRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var source: FreeReviewCardsSourceImpl

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
        source = FreeReviewCardsSourceImpl(db.flashcardDao())
        runBlocking { seed() }
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun deck(id: String, deletedAt: Long? = null) = db.deckDao().addDeck(
        DeckEntity(id = id, name = id, createdAt = ms(1), updatedAt = ms(deletedAt ?: 1), description = null, deletedAt = deletedAt?.let { ms(it) })
    )

    private suspend fun topic(id: String, deckId: String, deletedAt: Long? = null) = db.topicDao().addTopic(
        TopicEntity(id = id, deckId = deckId, name = id, createdAt = ms(1), updatedAt = ms(deletedAt ?: 1), deletedAt = deletedAt?.let { ms(it) })
    )

    private suspend fun card(id: String, deckId: String, nextReview: Long?, topicId: String? = null, deleted: Boolean = false) =
        RoomTransactionRunner(db).inTransaction {
            db.flashcardDao().addFlashcard(
                FlashcardEntity(id = id, deckId = deckId, topicId = topicId, question = "Q$id", answer = "A$id", cardType = CardType.FLIP,
                    createdAt = ms(1), updatedAt = ms(1), deletedAt = if (deleted) ms(2) else null)
            )
            if (nextReview != null) {
                db.cardSchedulingDao().insert(
                    CardSchedulingEntity(cardId = id, easeFactor = 2.5, interval = 1, repetitions = 1, nextReview = ms(nextReview),
                        updatedAt = ms(1), userId = null, lastSyncedAt = null)
                )
            }
        }

    private suspend fun seed() {
        deck("A")
        topic("tX", "A", deletedAt = 3)
        card("a1", "A", nextReview = null)
        card("a2", "A", nextReview = 5_000)
        card("a3", "A", nextReview = 99_000)                 // FUTURA: no esta pendiente, pero el repaso libre la usa
        card("a4", "A", nextReview = 5_000, deleted = true)
        card("a5", "A", nextReview = 5_000, topicId = "tX")  // tema borrado: la tarjeta sigue activa
        deck("B", deletedAt = 4)                             // tombstone con tarjetas VIVAS
        card("b1", "B", nextReview = null)
        card("b2", "B", nextReview = 5_000)
        deck("C")
        card("c1", "C", nextReview = 99_000)
    }

    private suspend fun all() = db.flashcardDao().getFreeReviewCards().map { it.id }
    private suspend fun byDeck(id: String) = db.flashcardDao().getFreeReviewCardsByDeck(id).map { it.id }

    // Mutacion: usar el filtro de pendientes (nextReview <= now) en lugar de todas: a3 y c1 (futuras) desaparecerian.
    @Test
    fun todas_las_activas_incluidas_las_que_NO_estan_pendientes() = runBlocking {
        assertEquals(listOf("a1", "a2", "a3", "a5", "c1"), all())
        assertTrue("a3" !in db.flashcardDao().getAllDueFlashcards(ms(10_000)).map { it.id }, "a3 no esta pendiente: el repaso libre es otra consulta")
    }

    // Mutacion: quitar LIVE_DECK_JOIN (o usar getFlashcardsByDeckId): b1 y b2 (vivas bajo un mazo en tombstone) entrarian.
    @Test
    fun una_tarjeta_viva_bajo_un_mazo_en_tombstone_no_entra() = runBlocking {
        val ids = all()
        assertTrue("b1" !in ids && "b2" !in ids, "ids = $ids")
        assertEquals(emptyList(), byDeck("B"), "el mazo borrado no ofrece ninguna")
    }

    @Test
    fun una_tarjeta_borrada_no_entra_y_el_tema_borrado_no_la_excluye() = runBlocking {
        val ids = all()
        assertTrue("a4" !in ids)
        assertTrue("a5" in ids)
    }

    // Mutacion: quitar el filtro de deckId del ambito de un mazo.
    @Test
    fun el_ambito_de_un_mazo_solo_trae_ese_mazo() = runBlocking {
        assertEquals(listOf("a1", "a2", "a3", "a5"), byDeck("A"))
        assertEquals(listOf("c1"), byDeck("C"))
        assertEquals(emptyList(), byDeck("no-existe"))
    }

    @Test
    fun el_orden_base_es_por_id() = runBlocking {
        val ids = all()
        assertEquals(ids.sorted(), ids)
    }

    @Test
    fun la_fuente_mapea_las_tarjetas_de_dominio() = runBlocking {
        val cards = source.getAll()
        assertEquals(listOf("a1", "a2", "a3", "a5", "c1"), cards.map { it.id })
        assertEquals("Qa1", cards.first().question)
        assertEquals("A", cards.first().deckId)
        assertEquals(listOf("c1"), source.getByDeck("C").map { it.id })
    }

    @Test
    fun el_caso_de_uso_baraja_con_semilla_sobre_la_consulta_real() = runBlocking {
        val useCase = GetFreeReviewCardsUseCase(source, SeededCardShuffler)
        val one = useCase(StudyScope.All, 5L).map { it.id }
        val two = useCase(StudyScope.All, 5L).map { it.id }
        assertEquals(one, two, "la misma semilla reproduce el mismo orden")
        assertEquals(listOf("a1", "a2", "a3", "a5", "c1"), one.sorted())
    }

    @Test
    fun mazo_grande_5000_tarjetas_correcto_y_sin_perder_ninguna() = runBlocking {
        deck("BIG")
        RoomTransactionRunner(db).inTransaction {
            for (i in 1..5000) {
                db.flashcardDao().addFlashcard(
                    FlashcardEntity(id = "big%05d".format(i), deckId = "BIG", topicId = null, question = "Q$i", answer = "A$i", cardType = CardType.FLIP,
                        createdAt = ms(1), updatedAt = ms(1), deletedAt = null)
                )
            }
        }
        val useCase = GetFreeReviewCardsUseCase(source, SeededCardShuffler)
        val cards = useCase(StudyScope.Deck("BIG"), 11L)
        assertEquals(5000, cards.size)
        assertEquals(5000, cards.map { it.id }.toSet().size, "sin duplicados")
        assertEquals((1..5000).map { "big%05d".format(it) }, cards.map { it.id }.sorted())
        assertTrue(cards.map { it.id } != cards.map { it.id }.sorted(), "esta barajado")
    }
}
