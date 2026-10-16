package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.domain.models.CardType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Prueba las consultas "due" contra una Room real en memoria (la logica esta en el SQL
 * de FlashcardDao, no en codigo Kotlin, asi que un fake no la cubriria).
 * Regresion: una tarjeta nueva (sin fila en card_scheduling) tiene que salir como pendiente.
 */
class FlashcardDaoDueQueriesTest {

    private lateinit var db: AppDatabase
    private val now = Instant.fromEpochMilliseconds(1_000_000)
    private val past = Instant.fromEpochMilliseconds(500_000)
    private val future = Instant.fromEpochMilliseconds(2_000_000)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        runBlocking {
            db.deckDao().addDeck(DeckEntity(id = "deck-1", name = "D1", createdAt = past, updatedAt = past, description = null))
            db.deckDao().addDeck(DeckEntity(id = "deck-2", name = "D2", createdAt = past, updatedAt = past, description = null))
        }
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun addCard(id: String, deckId: String = "deck-1", deletedAt: Instant? = null) {
        db.flashcardDao().addFlashcard(
            FlashcardEntity(
                id = id, deckId = deckId, question = "Q$id", answer = "A$id",
                cardType = CardType.FLIP, createdAt = past, updatedAt = past, deletedAt = deletedAt
            )
        )
    }

    private suspend fun addScheduling(cardId: String, nextReview: Instant) {
        db.cardSchedulingDao().insert(
            CardSchedulingEntity(
                cardId = cardId, easeFactor = 2.5, interval = 1, repetitions = 1,
                nextReview = nextReview, updatedAt = past,
                userId = null, lastSyncedAt = null
            )
        )
    }

    @Test
    fun una_tarjeta_nueva_sin_fila_de_scheduling_sale_como_pendiente_por_mazo() = runBlocking {
        addCard("new")

        val due = db.flashcardDao().getDueFlashcardsByDeck("deck-1", now)

        assertEquals(listOf("new"), due.map { it.id })
    }

    @Test
    fun una_tarjeta_nueva_sin_fila_de_scheduling_sale_como_pendiente_en_todos_los_mazos() = runBlocking {
        addCard("new", deckId = "deck-2")

        val due = db.flashcardDao().getAllDueFlashcards(now)

        assertEquals(listOf("new"), due.map { it.id })
    }

    @Test
    fun una_tarjeta_con_nextReview_futuro_no_sale_y_con_nextReview_pasado_si() = runBlocking {
        addCard("later")
        addCard("overdue")
        addScheduling("later", future)
        addScheduling("overdue", past)

        assertEquals(listOf("overdue"), db.flashcardDao().getDueFlashcardsByDeck("deck-1", now).map { it.id })
        assertEquals(listOf("overdue"), db.flashcardDao().getAllDueFlashcards(now).map { it.id })
    }

    @Test
    fun una_tarjeta_borrada_no_sale_aunque_no_tenga_fila_de_scheduling() = runBlocking {
        addCard("deleted", deletedAt = past)

        assertEquals(emptyList(), db.flashcardDao().getDueFlashcardsByDeck("deck-1", now))
        assertEquals(emptyList(), db.flashcardDao().getAllDueFlashcards(now))
    }

    @Test
    fun las_tarjetas_nuevas_van_primero_y_el_resto_por_nextReview_ascendente() = runBlocking {
        addCard("scheduled-late")
        addCard("scheduled-early")
        addCard("new")
        addScheduling("scheduled-late", Instant.fromEpochMilliseconds(900_000))
        addScheduling("scheduled-early", Instant.fromEpochMilliseconds(600_000))

        val due = db.flashcardDao().getDueFlashcardsByDeck("deck-1", now)

        assertEquals(listOf("new", "scheduled-early", "scheduled-late"), due.map { it.id })
    }

    @Test
    fun tras_repasar_una_tarjeta_nueva_su_fila_se_crea_y_entra_en_el_ciclo_normal() = runBlocking {
        addCard("new")
        assertEquals(null, db.cardSchedulingDao().getByCardId("new"))

        // Primer repaso: se crea la fila con vencimiento futuro -> deja de estar pendiente.
        addScheduling("new", future)

        assertEquals(future, db.cardSchedulingDao().getByCardId("new")?.nextReview)
        assertEquals(emptyList(), db.flashcardDao().getDueFlashcardsByDeck("deck-1", now))
    }
}
