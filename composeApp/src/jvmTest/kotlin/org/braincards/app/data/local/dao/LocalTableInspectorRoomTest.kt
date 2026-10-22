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
import org.braincards.app.data.repository.LocalDataRepositoryImpl
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.SyncTable
import org.braincards.app.domain.repository.LocalTableInspector
import org.braincards.app.domain.repository.NeverEmptyLocalTableInspector
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * LocalTableInspector (implementado por LocalDataRepositoryImpl con EXISTS en los DAO) contra una
 * Room real: ¿esta tabla esta vacia? Cuenta TODAS las filas, tombstones incluidos.
 */
class LocalTableInspectorRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var inspector: LocalTableInspector
    private lateinit var localData: LocalDataRepositoryImpl

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        localData = LocalDataRepositoryImpl(
            deckDao = db.deckDao(), topicDao = db.topicDao(), flashcardDao = db.flashcardDao(),
            cardSchedulingDao = db.cardSchedulingDao(), reviewLogDao = db.reviewLogDao(),
            transactionRunner = RoomTransactionRunner(db)
        )
        inspector = localData
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun emptiness() = SyncTable.entries.associateWith { inspector.isEmpty(it) }

    private suspend fun addDeck(id: String = "deck-1", deleted: Boolean = false) = db.deckDao().addDeck(
        DeckEntity(id = id, name = id, createdAt = ms(1), updatedAt = ms(2), description = null,
            deletedAt = if (deleted) ms(2) else null, userId = "user-a")
    )

    private suspend fun addTopic(id: String = "topic-1", deckId: String = "deck-1", deleted: Boolean = false) =
        db.topicDao().addTopic(
            TopicEntity(id = id, deckId = deckId, name = id, createdAt = ms(1), updatedAt = ms(2),
                deletedAt = if (deleted) ms(2) else null, userId = "user-a")
        )

    private suspend fun addCard(id: String = "card-1", deckId: String = "deck-1", deleted: Boolean = false) =
        db.flashcardDao().addFlashcard(
            FlashcardEntity(id = id, deckId = deckId, question = "Q", answer = "A", cardType = CardType.FLIP,
                createdAt = ms(1), updatedAt = ms(2), deletedAt = if (deleted) ms(2) else null, userId = "user-a")
        )

    private suspend fun addScheduling(cardId: String = "card-1") = db.cardSchedulingDao().insert(
        CardSchedulingEntity(cardId = cardId, easeFactor = 2.5, interval = 1, repetitions = 1,
            nextReview = ms(5000), updatedAt = ms(2), userId = "user-a", lastSyncedAt = null)
    )

    private fun allEmpty() = SyncTable.entries.associateWith { true }

    // --- BD vacia ----------------------------------------------------------------------------------------

    @Test
    fun `con la BD vacia las cuatro tablas estan vacias`() = runBlocking {
        assertEquals(allEmpty(), emptiness())
    }

    // --- cada tabla por separado (la respuesta es de LA tabla preguntada) -------------------------------------------

    @Test
    fun `una fila en decks solo hace no vacia a decks`() = runBlocking {
        addDeck()

        assertEquals(allEmpty() + (SyncTable.DECKS to false), emptiness())
    }

    @Test
    fun `una fila en topics solo hace no vacia a topics`() = runBlocking {
        addDeck()
        addTopic()

        assertEquals(allEmpty() + (SyncTable.DECKS to false) + (SyncTable.TOPICS to false), emptiness())
    }

    @Test
    fun `una fila en flashcards solo hace no vacia a flashcards`() = runBlocking {
        addDeck()
        addCard()

        assertEquals(allEmpty() + (SyncTable.DECKS to false) + (SyncTable.FLASHCARDS to false), emptiness())
    }

    @Test
    fun `una fila en card_scheduling solo hace no vacia a card_scheduling`() = runBlocking {
        addDeck()
        addCard()
        addScheduling()

        assertEquals(
            mapOf(
                SyncTable.DECKS to false, SyncTable.TOPICS to true,
                SyncTable.FLASHCARDS to false, SyncTable.CARD_SCHEDULING to false
            ),
            emptiness()
        )
    }

    // --- tombstones: una tabla con SOLO borrados logicos NO esta vacia ---------------------------------------------

    @Test
    fun `una tabla con solo tombstones no esta vacia`() = runBlocking {
        addDeck(deleted = true)
        addTopic(deleted = true)
        addCard(deleted = true)

        assertFalse(inspector.isEmpty(SyncTable.DECKS), "decks con solo un tombstone")
        assertFalse(inspector.isEmpty(SyncTable.TOPICS), "topics con solo un tombstone")
        assertFalse(inspector.isEmpty(SyncTable.FLASHCARDS), "flashcards con solo un tombstone")
        assertTrue(inspector.isEmpty(SyncTable.CARD_SCHEDULING))
    }

    @Test
    fun `una fila viva y un tombstone en la misma tabla tampoco la dejan vacia`() = runBlocking {
        addDeck("deck-vivo")
        addDeck("deck-borrado", deleted = true)

        assertFalse(inspector.isEmpty(SyncTable.DECKS))
    }

    // --- vaciar la BD ---------------------------------------------------------------------------------------------------

    @Test
    fun `tras vaciar la BD las cuatro tablas vuelven a estar vacias`() = runBlocking {
        addDeck()
        addTopic()
        addCard()
        addScheduling()
        assertEquals(SyncTable.entries.associateWith { false }, emptiness())

        localData.wipeAll()

        assertEquals(allEmpty(), emptiness())
    }

    // --- valor por defecto del motor ---------------------------------------------------------------------------------------

    @Test
    fun `el valor por defecto nunca dice vacia aunque la BD lo este`() = runBlocking {
        for (table in SyncTable.entries) {
            assertFalse(NeverEmptyLocalTableInspector.isEmpty(table), "$table")
        }
    }
}
