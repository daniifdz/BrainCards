package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.local.entity.TopicEntity
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Borrar un mazo (DeckRepositoryImpl.deleteDeck) contra una Room real en memoria, con foreign_keys = ON:
 *  - es ATOMICO: si falla a mitad no queda nada borrado (se provoca el fallo en el segundo borrado de tarjeta);
 *  - sin fallo, el resultado es el de siempre: mazo, temas y tarjetas vivos del mazo con deletedAt = updatedAt = now
 *    (la misma marca), lo ya borrado conserva su marca y las filas de otros mazos quedan intactas.
 */
class DeckDeleteRoomTest {

    private lateinit var db: AppDatabase

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)
    private val now = ms(9_000)
    private val fixedClock = object : Clock {
        override fun now(): Instant = now
    }

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
        runBlocking { seed() }
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun repository(flashcardDao: FlashcardDao = db.flashcardDao()) =
        DeckRepositoryImpl(db.deckDao(), db.topicDao(), flashcardDao, RoomTransactionRunner(db), fixedClock)

    private fun deckToDelete() =
        Deck(id = "d1", name = "d1", createdAt = ms(1), updatedAt = ms(1), description = null)

    private suspend fun deck(id: String) = db.deckDao().addDeck(
        DeckEntity(id = id, name = id, createdAt = ms(1), updatedAt = ms(1), description = null,
            lastSyncedAt = ms(1), userId = "u")
    )

    private suspend fun topic(id: String, deckId: String, deletedAt: Long? = null) = db.topicDao().addTopic(
        TopicEntity(id = id, deckId = deckId, name = id, createdAt = ms(1), updatedAt = ms(deletedAt ?: 1),
            deletedAt = deletedAt?.let { ms(it) }, lastSyncedAt = ms(1), userId = "u")
    )

    private suspend fun card(id: String, deckId: String, topicId: String?, deletedAt: Long? = null) =
        db.flashcardDao().addFlashcard(
            FlashcardEntity(id = id, deckId = deckId, topicId = topicId, question = "Q", answer = "A",
                cardType = CardType.FLIP, createdAt = ms(1), updatedAt = ms(deletedAt ?: 1),
                deletedAt = deletedAt?.let { ms(it) }, lastSyncedAt = ms(1), userId = "u")
        )

    private suspend fun seed() {
        deck("d1"); deck("d2")
        topic("t1a", "d1"); topic("t1b", "d1"); topic("t1-old", "d1", deletedAt = 500); topic("t2", "d2")
        card("c1", "d1", "t1a"); card("c2", "d1", null); card("c1-old", "d1", "t1b", deletedAt = 600)
        card("c3", "d2", "t2"); card("c4", "d2", null)
    }

    private suspend fun deckRow(id: String) = db.deckDao().getDeckByIdIncludingDeleted(id)!!
    private suspend fun topicRow(id: String) = db.topicDao().getTopicByIdIncludingDeleted(id)!!
    private suspend fun cardRow(id: String) = db.flashcardDao().getFlashcardByIdIncludingDeleted(id)!!

    private suspend fun assertUntouched(vararg ids: String) {
        for (id in ids) {
            val row = when {
                id.startsWith("d") -> deckRow(id).let { Triple(it.deletedAt, it.updatedAt, it.lastSyncedAt) }
                id.startsWith("t") -> topicRow(id).let { Triple(it.deletedAt, it.updatedAt, it.lastSyncedAt) }
                else -> cardRow(id).let { Triple(it.deletedAt, it.updatedAt, it.lastSyncedAt) }
            }
            assertEquals(Triple(null, ms(1), ms(1)), row, "$id debe seguir intacta")
        }
    }

    // --- sin fallo: mismo resultado que siempre ---------------------------------------------------------------------

    @Test
    fun sin_fallo_marca_mazo_temas_y_tarjetas_vivas_con_la_misma_marca() = runBlocking<Unit> {
        repository().deleteDeck(deckToDelete())

        for (row in listOf(
            deckRow("d1").let { it.deletedAt to it.updatedAt },
            topicRow("t1a").let { it.deletedAt to it.updatedAt },
            topicRow("t1b").let { it.deletedAt to it.updatedAt },
            cardRow("c1").let { it.deletedAt to it.updatedAt },
            cardRow("c2").let { it.deletedAt to it.updatedAt }   // sin tema: tambien
        )) {
            assertEquals(now to now, row, "deletedAt = updatedAt = now")
        }
        // Siguen "sucias" para el push: updatedAt > lastSyncedAt.
        assertEquals(ms(1), deckRow("d1").lastSyncedAt)
    }

    @Test
    fun sin_fallo_lo_ya_borrado_conserva_su_marca_y_los_otros_mazos_quedan_intactos() = runBlocking<Unit> {
        repository().deleteDeck(deckToDelete())

        assertEquals(ms(500), topicRow("t1-old").deletedAt)
        assertEquals(ms(500), topicRow("t1-old").updatedAt)
        assertEquals(ms(600), cardRow("c1-old").deletedAt)
        assertEquals(ms(600), cardRow("c1-old").updatedAt)
        assertUntouched("d2", "t2", "c3", "c4")
    }

    @Test
    fun el_mazo_borrado_desaparece_de_las_lecturas_normales() = runBlocking<Unit> {
        repository().deleteDeck(deckToDelete())

        assertNull(db.deckDao().getDeckById("d1"))
        assertEquals(emptyList(), db.flashcardDao().getAllDueFlashcards(ms(99_999)).filter { it.deckId == "d1" })
    }

    // --- con fallo: atomico -----------------------------------------------------------------------------------------

    /** FlashcardDao real, pero el SEGUNDO borrado logico de tarjeta falla (el primero ya se ha ejecutado). */
    private fun failingOnSecondCardDelete(): FlashcardDao = object : FlashcardDao by db.flashcardDao() {
        private var calls = 0
        override suspend fun softdeleteFlashcardById(id: String, deletedAt: Instant) {
            if (++calls == 2) throw IllegalStateException("fallo provocado a mitad del borrado")
            db.flashcardDao().softdeleteFlashcardById(id, deletedAt)
        }
    }

    @Test
    fun si_falla_a_mitad_no_queda_nada_borrado() = runBlocking<Unit> {
        assertFailsWith<IllegalStateException> { repository(failingOnSecondCardDelete()).deleteDeck(deckToDelete()) }

        // Ni el mazo, ni los temas, ni la tarjeta que SI llego a marcarse antes del fallo: todo revertido.
        assertUntouched("d1", "t1a", "t1b", "c1", "c2", "d2", "t2", "c3", "c4")
        assertEquals(ms(500), topicRow("t1-old").deletedAt)
        assertEquals(ms(600), cardRow("c1-old").deletedAt)
    }

    @Test
    fun tras_un_fallo_revertido_se_puede_borrar_el_mazo_normalmente() = runBlocking<Unit> {
        assertFailsWith<IllegalStateException> { repository(failingOnSecondCardDelete()).deleteDeck(deckToDelete()) }

        repository().deleteDeck(deckToDelete())

        assertEquals(now, deckRow("d1").deletedAt)
        assertEquals(now, topicRow("t1a").deletedAt)
        assertEquals(now, cardRow("c1").deletedAt)
        assertEquals(now, cardRow("c2").deletedAt)
    }
}
