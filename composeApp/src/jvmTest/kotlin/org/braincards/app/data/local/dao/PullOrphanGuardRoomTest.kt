package org.braincards.app.data.local.dao

import org.braincards.app.data.remote.SupabaseFailureClassifier
import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.TopicEntity
import org.braincards.app.data.repository.CardSchedulingRepositoryImpl
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.FlashcardRepositoryImpl
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.PullApplyReport
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import org.braincards.app.domain.usecase.Sync.SyncResult
import org.braincards.app.domain.usecase.Sync.discardedRowCount
import org.braincards.app.domain.usecase.Sync.isFullySuccessful
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guarda de padres del pull de topics y flashcards contra una Room real en memoria (con
 * foreign_keys = ON, que es lo que hace fallar un insert huerfano): las filas cuyo deck/topic no
 * existe localmente se descartan y se cuentan, sin abortar la tabla.
 */
class PullOrphanGuardRoomTest {

    private val userId = "user-a"
    private lateinit var db: AppDatabase
    private lateinit var decks: DeckRepositoryImpl
    private lateinit var topics: TopicRepositoryImpl
    private lateinit var flashcards: FlashcardRepositoryImpl
    private lateinit var scheduling: CardSchedulingRepositoryImpl

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        decks = DeckRepositoryImpl(db.deckDao(), db.topicDao(), db.flashcardDao(), RoomTransactionRunner(db))
        topics = TopicRepositoryImpl(db.topicDao(), db.flashcardDao(), RoomTransactionRunner(db))
        flashcards = FlashcardRepositoryImpl(db.flashcardDao())
        scheduling = CardSchedulingRepositoryImpl(db.cardSchedulingDao(), RoomTransactionRunner(db))
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun count(table: String): Long = db.useWriterConnection { transactor ->
        transactor.usePrepared("SELECT COUNT(*) FROM \"$table\"") { stmt ->
            stmt.step()
            stmt.getLong(0)
        }
    }

    private fun deck(id: String) = Deck(id, "Deck $id", ms(1000), ms(2000), null, userId = userId)

    private fun topic(id: String, deckId: String) =
        Topic(id, deckId, "Topic $id", ms(1000), ms(2000), userId = userId)

    private fun card(id: String, deckId: String, topicId: String? = null) = Flashcard(
        id = id, deckId = deckId, question = "Q", answer = "A", createdAt = ms(1000), updatedAt = ms(2000),
        cardType = CardType.FLIP, topicId = topicId, userId = userId
    )

    private fun schedulingFor(cardId: String) = CardScheduling(
        cardId = cardId, easeFactor = 2.5, interval = 1, repetitions = 1, nextReview = ms(5000),
        updatedAt = ms(2000), userId = userId, lastSyncedAt = null
    )

    private suspend fun seedLocalDeck(id: String, deleted: Boolean = false) = db.deckDao().addDeck(
        DeckEntity(id = id, name = id, createdAt = ms(1), updatedAt = ms(1), description = null,
            deletedAt = if (deleted) ms(2) else null, userId = userId)
    )

    private suspend fun seedLocalTopic(id: String, deckId: String, deleted: Boolean = false) = db.topicDao().addTopic(
        TopicEntity(id = id, deckId = deckId, name = id, createdAt = ms(1), updatedAt = ms(1),
            deletedAt = if (deleted) ms(2) else null, userId = userId)
    )

    @Test
    fun el_caso_de_la_cuenta_con_bd_vacia_no_aborta_ninguna_tabla_y_descarta_las_huerfanas() = runBlocking {
        val remote = FakeRemoteDataSource().apply {
            decksToPull = listOf(deck("deck-1"), deck("deck-2"))
            topicsToPull = emptyList()
            flashcardsToPull =
                // 3 validas: deck presente, sin topic
                listOf(card("f01", "deck-1"), card("f02", "deck-1"), card("f03", "deck-2")) +
                    // 4 huerfanas por topic: el topic pertenece a otra cuenta, no se descargo
                    (4..7).map { card("f%02d".format(it), "deck-1", topicId = "topic-ajeno") } +
                    // 5 huerfanas por deck: el deck pertenece a otra cuenta, no se descargo
                    (8..12).map { card("f%02d".format(it), "deck-ajeno") }
            // 9 scheduling: 3 de tarjetas validas + 6 de huerfanas
            cardSchedulingToPull = (1..9).map { schedulingFor("f%02d".format(it)) }
        }
        val auth = FakeAuthRepository(AuthState.Authenticated(userId, "a@b.c", sessionConfirmed = true))
        val engine = SyncEngineUseCase(
            auth, remote, decks, topics, flashcards, scheduling,
            BackfillLocalDataUseCase(decks, topics, flashcards, scheduling),
            failureClassifier = SupabaseFailureClassifier
        )

        val result = engine()

        val completed = assertIs<SyncResult.Completed>(result)
        // Ninguna tabla se aborta, y el sync cuenta como correcto aunque haya descartes.
        assertFalse(completed.pull.decks.aborted)
        assertFalse(completed.pull.topics.aborted)
        assertFalse(completed.pull.flashcards.aborted, "las huerfanas no deben abortar la tabla de flashcards")
        assertFalse(completed.pull.cardScheduling.aborted)
        assertTrue(completed.isFullySuccessful())
        // Las 9 huerfanas descartadas y contadas; las 3 validas aplicadas.
        assertEquals(3, completed.pull.flashcards.applied)
        assertEquals(9, completed.pull.flashcards.discarded)
        // Las 6 de scheduling sin flashcard local tambien se descartan (el motor ya las cuenta).
        assertEquals(6, completed.pull.cardScheduling.discarded)
        assertEquals(15, completed.pull.discardedRowCount())
        assertEquals(0, completed.pull.topics.discarded)
        // Estado local: los 2 decks, las 3 flashcards validas y SOLO el scheduling de esas 3.
        assertEquals(2L, count("decks"))
        assertEquals(0L, count("topics"))
        assertEquals(3L, count("flashcards"))
        assertEquals(3L, count("card_scheduling"))
    }

    @Test
    fun un_topic_cuyo_deck_no_existe_se_descarta_y_no_aborta_el_lote() = runBlocking {
        seedLocalDeck("deck-1")

        val report = topics.applyRemotePullReporting(
            listOf(topic("t-ok", "deck-1"), topic("t-huerfano", "deck-ajeno"))
        )

        assertEquals(PullApplyReport(applied = 1, discarded = 1, discardedIds = listOf("t-huerfano")), report)
        assertEquals(1L, count("topics"))
        assertNotNull(db.topicDao().getTopicById("t-ok"))
        Unit
    }

    @Test
    fun un_topic_con_deck_tombstone_se_aplica_porque_el_deck_existe() = runBlocking {
        seedLocalDeck("deck-borrado", deleted = true)

        val report = topics.applyRemotePullReporting(listOf(topic("t1", "deck-borrado")))

        assertEquals(PullApplyReport(applied = 1, discarded = 0), report)
    }

    @Test
    fun una_flashcard_con_deck_valido_y_topic_inexistente_se_descarta() = runBlocking {
        seedLocalDeck("deck-1")

        val report = flashcards.applyRemotePullReporting(listOf(card("f1", "deck-1", topicId = "topic-ajeno")))

        assertEquals(PullApplyReport(applied = 0, discarded = 1, discardedIds = listOf("f1")), report)
        assertEquals(0L, count("flashcards"))
    }

    @Test
    fun una_flashcard_con_topic_valido_se_aplica_aunque_el_topic_sea_tombstone() = runBlocking {
        seedLocalDeck("deck-1")
        seedLocalTopic("topic-1", "deck-1")
        seedLocalTopic("topic-borrado", "deck-1", deleted = true)

        val report = flashcards.applyRemotePullReporting(
            listOf(card("f1", "deck-1", "topic-1"), card("f2", "deck-1", "topic-borrado"), card("f3", "deck-1"))
        )

        assertEquals(PullApplyReport(applied = 3, discarded = 0), report)
        assertEquals(3L, count("flashcards"))
    }

    @Test
    fun una_flashcard_con_deck_tombstone_se_aplica_porque_el_deck_existe() = runBlocking {
        seedLocalDeck("deck-borrado", deleted = true)

        val report = flashcards.applyRemotePullReporting(listOf(card("f1", "deck-borrado")))

        assertEquals(PullApplyReport(applied = 1, discarded = 0), report)
        assertEquals(1L, count("flashcards"))
    }

    @Test
    fun una_flashcard_con_deck_inexistente_se_descarta_aunque_su_topic_exista() = runBlocking {
        seedLocalDeck("deck-1")
        seedLocalTopic("topic-1", "deck-1")

        val report = flashcards.applyRemotePullReporting(listOf(card("f1", "deck-ajeno", "topic-1")))

        assertEquals(PullApplyReport(applied = 0, discarded = 1, discardedIds = listOf("f1")), report)
    }

    @Test
    fun los_ids_descartados_salen_en_el_orden_en_que_llegan_del_servidor() = runBlocking {
        seedLocalDeck("deck-1")
        seedLocalTopic("topic-1", "deck-1")

        val topicReport = topics.applyRemotePullReporting(
            listOf(topic("t-z", "deck-ajeno"), topic("t-ok", "deck-1"), topic("t-a", "deck-ajeno"))
        )
        val cardReport = flashcards.applyRemotePullReporting(
            listOf(
                card("f-z", "deck-ajeno"), card("f-ok", "deck-1", "topic-1"),
                card("f-a", "deck-1", topicId = "topic-ajeno"), card("f-m", "deck-ajeno")
            )
        )

        assertEquals(listOf("t-z", "t-a"), topicReport.discardedIds)
        assertEquals(listOf("f-z", "f-a", "f-m"), cardReport.discardedIds)
        assertEquals(2, topicReport.discarded)
        assertEquals(3, cardReport.discarded)
    }

    @Test
    fun applyRemotePull_sigue_devolviendo_solo_las_filas_aplicadas() = runBlocking {
        seedLocalDeck("deck-1")

        val appliedTopics = topics.applyRemotePull(listOf(topic("t1", "deck-1"), topic("t2", "deck-ajeno")))
        val appliedCards = flashcards.applyRemotePull(listOf(card("f1", "deck-1"), card("f2", "deck-ajeno")))

        assertEquals(1, appliedTopics)
        assertEquals(1, appliedCards)
    }

    @Test
    fun un_pull_con_todas_las_filas_validas_no_descarta_nada() = runBlocking {
        seedLocalDeck("deck-1")

        val topicReport = topics.applyRemotePullReporting(listOf(topic("t1", "deck-1"), topic("t2", "deck-1")))
        val cardReport = flashcards.applyRemotePullReporting(
            listOf(card("f1", "deck-1", "t1"), card("f2", "deck-1"))
        )

        assertEquals(PullApplyReport(applied = 2, discarded = 0), topicReport)
        assertEquals(PullApplyReport(applied = 2, discarded = 0), cardReport)
    }

    @Test
    fun una_lista_vacia_no_toca_la_bd_ni_descarta() = runBlocking {
        assertEquals(PullApplyReport(0, 0), topics.applyRemotePullReporting(emptyList()))
        assertEquals(PullApplyReport(0, 0), flashcards.applyRemotePullReporting(emptyList()))
    }
}
