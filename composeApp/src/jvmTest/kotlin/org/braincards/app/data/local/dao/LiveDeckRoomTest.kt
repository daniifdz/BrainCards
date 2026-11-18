package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.local.entity.TopicEntity
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.DueCardsObserverImpl
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Deuda (e): tarjetas VIVAS bajo un mazo en tombstone (llegadas por sync) no cuentan en Home, ni entran en la cola de
 * estudio, ni en las estadisticas. Una Room real en memoria con foreign_keys = ON; la regla unica es LIVE_DECK_JOIN.
 *
 * Siembra: mazo A vivo (a1 y a2 vencidas, a3 futura, a4 borrada, a5 vencida con un tema BORRADO) y mazo B en tombstone
 * con tarjetas vivas (b1 y b2 vencidas, b3 futura).
 */
class LiveDeckRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var observer: DueCardsObserverImpl

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)
    private val now = ms(10_000)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
        observer = DueCardsObserverImpl(db.flashcardDao())
        runBlocking { seed() }
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun deck(id: String, deletedAt: Long? = null) = db.deckDao().addDeck(
        DeckEntity(id = id, name = id, createdAt = ms(1), updatedAt = ms(deletedAt ?: 1), description = null,
            deletedAt = deletedAt?.let { ms(it) })
    )

    private suspend fun topic(id: String, deckId: String, deletedAt: Long? = null) = db.topicDao().addTopic(
        TopicEntity(id = id, deckId = deckId, name = id, createdAt = ms(1), updatedAt = ms(deletedAt ?: 1),
            deletedAt = deletedAt?.let { ms(it) })
    )

    /** [nextReview] null = sin fila de scheduling (nunca repasada: vencida). */
    private suspend fun card(
        id: String, deckId: String, nextReview: Long?, topicId: String? = null, deleted: Boolean = false
    ) = RoomTransactionRunner(db).inTransaction {
        db.flashcardDao().addFlashcard(
            FlashcardEntity(id = id, deckId = deckId, topicId = topicId, question = "Q$id", answer = "A$id",
                cardType = CardType.FLIP, createdAt = ms(1), updatedAt = ms(1), deletedAt = if (deleted) ms(2) else null)
        )
        if (nextReview != null) {
            db.cardSchedulingDao().insert(
                CardSchedulingEntity(cardId = id, easeFactor = 2.5, interval = 1, repetitions = 1,
                    nextReview = ms(nextReview), updatedAt = ms(1), userId = null, lastSyncedAt = null)
            )
        }
    }

    private suspend fun seed() {
        deck("A")
        topic("tX", "A", deletedAt = 3)             // tema borrado (mazo vivo)
        card("a1", "A", nextReview = null)          // vencida (sin scheduling)
        card("a2", "A", nextReview = 5_000)         // vencida
        card("a3", "A", nextReview = 99_000)        // futura
        card("a4", "A", nextReview = 5_000, deleted = true) // borrada
        card("a5", "A", nextReview = 5_000, topicId = "tX") // tema borrado: sigue activa

        deck("B", deletedAt = 4)                    // mazo en tombstone con tarjetas VIVAS
        card("b1", "B", nextReview = null)
        card("b2", "B", nextReview = 5_000)
        card("b3", "B", nextReview = 99_000)
    }

    private suspend fun count() = observer.observeDueCount(flowOf(now)).first()
    private suspend fun dueAll() = db.flashcardDao().getAllDueFlashcards(now).map { it.id }.sorted()

    // ---- contador de Home y cola de "Estudiar Todo" ----

    // Mutacion M1: si observeDueCount no filtrara el mazo vivo, este test fallaria (contaria b1 y b2: 5).
    @Test
    fun el_contador_de_Home_no_cuenta_las_tarjetas_de_un_mazo_borrado() = runBlocking<Unit> {
        assertEquals(3, count()) // a1, a2 y a5 (tema borrado, mazo vivo); no b1 ni b2
    }

    // Mutacion M2: si getAllDueFlashcards no filtrara el mazo vivo, este test fallaria.
    @Test
    fun la_cola_de_Estudiar_Todo_no_incluye_las_tarjetas_de_un_mazo_borrado() = runBlocking<Unit> {
        assertEquals(listOf("a1", "a2", "a5"), dueAll())
    }

    @Test
    fun el_contador_y_la_cola_siguen_coincidiendo_para_cualquier_ahora() = runBlocking<Unit> {
        listOf(0L, 4_999L, 5_000L, 10_000L, 98_999L, 99_000L, 200_000L).forEach { t ->
            val queue = db.flashcardDao().getAllDueFlashcards(ms(t)).size
            val counter = observer.observeDueCount(flowOf(ms(t))).first()
            assertEquals(queue, counter, "now=$t")
        }
    }

    // ---- cola de un mazo concreto ----

    @Test
    fun la_cola_de_un_mazo_borrado_esta_vacia_y_la_de_uno_vivo_no() = runBlocking<Unit> {
        assertEquals(emptyList(), db.flashcardDao().getDueFlashcardsByDeck("B", now).map { it.id })
        assertEquals(listOf("a1", "a2", "a5"), db.flashcardDao().getDueFlashcardsByDeck("A", now).map { it.id }.sorted())
    }

    // ---- estadisticas ----

    @Test
    fun getAllFlashcards_excluye_las_tarjetas_de_un_mazo_borrado_y_las_borradas() = runBlocking<Unit> {
        val ids = db.flashcardDao().getAllFlashcards().first().map { it.id }.sorted()
        assertEquals(listOf("a1", "a2", "a3", "a5"), ids) // no a4 (borrada) ni b1..b3 (mazo borrado)
    }

    // ---- la sesion de estudio comprueba cada tarjeta antes de mostrarla ----

    // Mutacion M3: si la regla compartida dijera IS NOT NULL, este test (y la mayoria) fallaria.
    @Test
    fun getFlashcardById_devuelve_las_activas_y_null_para_borradas_o_de_mazo_borrado() = runBlocking<Unit> {
        assertNotNull(db.flashcardDao().getFlashcardById("a1"))
        assertNotNull(db.flashcardDao().getFlashcardById("a5")) // tema borrado: el mazo manda
        assertNull(db.flashcardDao().getFlashcardById("a4"))    // tarjeta borrada
        assertNull(db.flashcardDao().getFlashcardById("b1"))    // mazo borrado
    }

    // ---- lo que NO debe cambiar ----

    // Mutacion G1: si getFlashcardsByDeckId filtrara el mazo vivo, deleteDeck no encontraria tarjetas que borrar.
    @Test
    fun getFlashcardsByDeckId_sigue_viendo_las_tarjetas_de_un_mazo_borrado_la_cascada_las_necesita() = runBlocking<Unit> {
        val ids = db.flashcardDao().getFlashcardsByDeckId("B").first().map { it.id }.sorted()
        assertEquals(listOf("b1", "b2", "b3"), ids)
    }

    @Test
    fun las_lecturas_de_sync_siguen_viendo_la_tarjeta_de_un_mazo_borrado() = runBlocking<Unit> {
        assertNotNull(db.flashcardDao().getFlashcardByIdIncludingDeleted("b1"))
    }

    // ---- un mazo que vuelve a estar vivo (pull remoto) recupera sus tarjetas ----

    @Test
    fun un_mazo_borrado_que_vuelve_a_estar_vivo_por_pull_recupera_sus_tarjetas_sin_cascada() = runBlocking<Unit> {
        val decks = DeckRepositoryImpl(db.deckDao(), db.topicDao(), db.flashcardDao(), RoomTransactionRunner(db))
        assertEquals(3, count())

        val applied = decks.applyRemotePull(
            listOf(Deck(id = "B", name = "B", createdAt = ms(1), updatedAt = ms(50), description = null, deletedAt = null))
        )

        assertEquals(1, applied)
        assertEquals(5, count()) // a1, a2, a5 + b1, b2: sus tarjetas seguian vivas
        assertEquals(listOf("a1", "a2", "a5", "b1", "b2"), dueAll())
    }
}
