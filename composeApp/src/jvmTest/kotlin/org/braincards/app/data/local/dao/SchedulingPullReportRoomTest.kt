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
import org.braincards.app.data.repository.CardSchedulingRepositoryImpl
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.PullApplyReport
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * applyRemotePullReporting de card_scheduling contra una Room real: informa de las filas DESCARTADAS
 * por no tener su flashcard en local (con su cardId), sin confundirlas con las que pierden el LWW.
 */
class SchedulingPullReportRoomTest {

    private val user = "user-a"
    private lateinit var db: AppDatabase
    private lateinit var repository: CardSchedulingRepositoryImpl

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = CardSchedulingRepositoryImpl(db.cardSchedulingDao(), RoomTransactionRunner(db))
        runBlocking {
            db.deckDao().addDeck(DeckEntity(id = "deck-1", name = "D", createdAt = ms(1), updatedAt = ms(1), description = null, userId = user))
        }
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun addCard(id: String) = db.flashcardDao().addFlashcard(
        FlashcardEntity(id = id, deckId = "deck-1", question = "Q", answer = "A", cardType = CardType.FLIP,
            createdAt = ms(1), updatedAt = ms(1), userId = user)
    )

    private fun remote(cardId: String, updatedAt: Long = 2000, repetitions: Int = 1) = CardScheduling(
        cardId = cardId, easeFactor = 2.5, interval = 1, repetitions = repetitions,
        nextReview = ms(5000), updatedAt = ms(updatedAt), userId = user, lastSyncedAt = null
    )

    @Test
    fun `una fila con flashcard local se aplica y no se descarta`() = runBlocking {
        addCard("c1")

        val report = repository.applyRemotePullReporting(listOf(remote("c1")))

        assertEquals(PullApplyReport(applied = 1, discarded = 0, discardedIds = emptyList()), report)
    }

    @Test
    fun `una fila sin flashcard local se descarta y se informa de su cardId`() = runBlocking {
        addCard("c1")

        val report = repository.applyRemotePullReporting(listOf(remote("c1"), remote("sin-flashcard")))

        assertEquals(PullApplyReport(applied = 1, discarded = 1, discardedIds = listOf("sin-flashcard")), report)
        assertEquals(null, db.cardSchedulingDao().getByCardId("sin-flashcard"))
    }

    @Test
    fun `los ids descartados salen en el orden en que llegan del servidor`() = runBlocking {
        addCard("c2")

        val report = repository.applyRemotePullReporting(
            listOf(remote("z-huerfana"), remote("c2"), remote("a-huerfana"), remote("m-huerfana"))
        )

        assertEquals(listOf("z-huerfana", "a-huerfana", "m-huerfana"), report.discardedIds)
        assertEquals(3, report.discarded)
    }

    @Test
    fun `una fila que pierde el LWW no es un descarte`() = runBlocking {
        addCard("c1")
        db.cardSchedulingDao().insert(
            CardSchedulingEntity(cardId = "c1", easeFactor = 2.5, interval = 1, repetitions = 5,
                nextReview = ms(5000), updatedAt = ms(9000), userId = user, lastSyncedAt = null)
        )

        val report = repository.applyRemotePullReporting(listOf(remote("c1", updatedAt = 2000)))

        assertEquals(PullApplyReport(applied = 0, discarded = 0, discardedIds = emptyList()), report)
        assertEquals(5, db.cardSchedulingDao().getByCardId("c1")!!.repetitions, "la local mas nueva se conserva")
    }

    @Test
    fun `todas huerfanas no aborta ni escribe nada`() = runBlocking {
        val report = repository.applyRemotePullReporting(listOf(remote("x1"), remote("x2")))

        assertEquals(PullApplyReport(applied = 0, discarded = 2, discardedIds = listOf("x1", "x2")), report)
    }

    @Test
    fun `una lista vacia devuelve un informe vacio`() = runBlocking {
        assertEquals(PullApplyReport(applied = 0), repository.applyRemotePullReporting(emptyList()))
    }

    @Test
    fun `applyRemotePull sigue devolviendo solo el numero de filas aplicadas`() = runBlocking {
        addCard("c1")

        val applied = repository.applyRemotePull(listOf(remote("c1"), remote("sin-flashcard")))

        assertEquals(1, applied)
    }
}
