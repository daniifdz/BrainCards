package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.local.entity.ReviewLogEntity
import org.braincards.app.data.local.entity.TopicEntity
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.FlashcardRepositoryImpl
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Un pull que reescribe un PADRE (insertAll con REPLACE = borrar + insertar en SQLite) no debe llevarse
 * por delante a sus hijos locales, con foreign_keys = ON como en produccion. Room real en memoria.
 */
class ParentPullKeepsChildrenRoomTest {

    private val userId = "user-a"
    private lateinit var db: AppDatabase
    private lateinit var decks: DeckRepositoryImpl
    private lateinit var topics: TopicRepositoryImpl
    private lateinit var flashcards: FlashcardRepositoryImpl

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
        decks = DeckRepositoryImpl(db.deckDao(), db.topicDao(), db.flashcardDao(), RoomTransactionRunner(db))
        topics = TopicRepositoryImpl(db.topicDao(), db.flashcardDao())
        flashcards = FlashcardRepositoryImpl(db.flashcardDao())
        // deck d1 -> topic t1 -> card c1 (+ scheduling c1, reviewlog r1), todo sincronizado.
        db.deckDao().addDeck(DeckEntity(id = "d1", name = "viejo", createdAt = ms(1), updatedAt = ms(1000),
            description = null, deletedAt = null, lastSyncedAt = ms(1000), userId = userId))
        db.topicDao().addTopic(TopicEntity(id = "t1", deckId = "d1", name = "tema", createdAt = ms(1),
            updatedAt = ms(1000), lastSyncedAt = ms(1000), userId = userId))
        db.flashcardDao().addFlashcard(FlashcardEntity(id = "c1", deckId = "d1", topicId = "t1", question = "Q",
            answer = "A", cardType = CardType.FLIP, createdAt = ms(1), updatedAt = ms(1000),
            lastSyncedAt = ms(1000), userId = userId))
        db.cardSchedulingDao().insert(CardSchedulingEntity(cardId = "c1", easeFactor = 2.1, interval = 9,
            repetitions = 4, nextReview = ms(777_000), updatedAt = ms(1000), userId = userId, lastSyncedAt = ms(1000)))
        db.reviewLogDao().addReviewLog(ReviewLogEntity(id = "r1", cardId = "c1", timestamp = ms(900), grade = 3,
            previousInterval = 1, newInterval = 9, userId = userId))
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun <R> query(sql: String, read: (androidx.sqlite.SQLiteStatement) -> R): List<R> =
        db.useWriterConnection { tx ->
            tx.usePrepared(sql) { st ->
                buildList { while (st.step()) add(read(st)) }
            }
        }

    private suspend fun count(table: String): Long = query("SELECT COUNT(*) FROM \"$table\"") { it.getLong(0) }.single()

    private suspend fun assertChildrenIntact(topics: Long = 1, cards: Long = 1) {
        assertEquals(topics, count("topics"), "topics")
        assertEquals(cards, count("flashcards"), "flashcards")
        assertEquals(1L, count("card_scheduling"), "card_scheduling")
        assertEquals(1L, count("reviewlog"), "reviewlog")
        val s = db.cardSchedulingDao().getByCardId("c1")!!
        assertEquals(4, s.repetitions)
        assertEquals(9, s.interval)
        assertEquals(ms(777_000), s.nextReview)
    }

    private fun remoteDeck(name: String = "renombrado", deletedAt: Instant? = null) = Deck(
        id = "d1", name = name, createdAt = ms(1), updatedAt = ms(5000), description = null,
        deletedAt = deletedAt, userId = userId
    )

    // --- esquema real: sin cascade y con foreign_keys activo ---

    @Test
    fun foreign_keys_esta_activo_y_ninguna_clave_foranea_tiene_cascade() = runBlocking {
        assertEquals(1L, query("PRAGMA foreign_keys") { it.getLong(0) }.single())
        for (table in listOf("topics", "flashcards", "card_scheduling")) {
            // columnas de PRAGMA foreign_key_list: id, seq, table, from, to, on_update, on_delete, match
            val onDelete = query("PRAGMA foreign_key_list(`$table`)") { it.getText(6) }
            assertEquals(true, onDelete.isNotEmpty(), "$table sin claves foraneas")
            onDelete.forEach { assertEquals("NO ACTION", it, "ON DELETE de $table") }
        }
        assertEquals(emptyList(), query("PRAGMA foreign_key_list(`reviewlog`)") { it.getText(2) })
    }

    // --- el padre se reescribe con REPLACE; los hijos sobreviven ---

    @Test
    fun un_mazo_renombrado_en_el_otro_dispositivo_conserva_temas_tarjetas_y_planificacion() = runBlocking {
        val applied = decks.applyRemotePull(listOf(remoteDeck()))

        assertEquals(1, applied)
        assertEquals("renombrado", db.deckDao().getDeckById("d1")!!.name)
        assertChildrenIntact()
    }

    @Test
    fun un_tema_editado_en_el_otro_dispositivo_conserva_sus_tarjetas_y_planificacion() = runBlocking {
        val applied = topics.applyRemotePull(listOf(
            Topic(id = "t1", deckId = "d1", name = "tema editado", createdAt = ms(1), updatedAt = ms(5000), userId = userId)
        ))

        assertEquals(1, applied)
        assertEquals("tema editado", db.topicDao().getTopicById("t1")!!.name)
        assertChildrenIntact()
    }

    @Test
    fun una_tarjeta_editada_en_el_otro_dispositivo_conserva_su_planificacion_y_su_historial() = runBlocking {
        val applied = flashcards.applyRemotePull(listOf(
            Flashcard(id = "c1", deckId = "d1", topicId = "t1", question = "Q editada", answer = "A", createdAt = ms(1),
                updatedAt = ms(5000), cardType = CardType.FLIP, userId = userId)
        ))

        assertEquals(1, applied)
        assertEquals("Q editada", db.flashcardDao().getFlashcardById("c1")!!.question)
        assertChildrenIntact()
    }

    @Test
    fun el_borrado_logico_de_un_mazo_no_borra_fisicamente_a_sus_hijos() = runBlocking {
        val applied = decks.applyRemotePull(listOf(remoteDeck(deletedAt = ms(5000))))

        assertEquals(1, applied)
        assertEquals(1L, query("SELECT COUNT(*) FROM decks WHERE id = 'd1' AND deletedAt IS NOT NULL") { it.getLong(0) }.single())
        assertChildrenIntact()
    }

    @Test
    fun editar_el_padre_varias_veces_seguidas_tampoco_pierde_hijos() = runBlocking {
        for (i in 1..3) {
            decks.applyRemotePull(listOf(remoteDeck("v$i").copy(updatedAt = ms(5000L + i))))
        }
        assertEquals("v3", db.deckDao().getDeckById("d1")!!.name)
        assertChildrenIntact()
    }
}
