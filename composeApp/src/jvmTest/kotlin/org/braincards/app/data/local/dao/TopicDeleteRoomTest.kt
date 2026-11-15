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
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Topic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Borrar un tema (TopicRepositoryImpl.deleteTopic) contra una Room real en memoria, con foreign_keys = ON:
 *  - tema y TODAS sus tarjetas vivas quedan con deletedAt = updatedAt = now (la MISMA marca);
 *  - no se toca nada mas: tarjetas de otros temas, tarjetas sin tema, otros mazos, ni lo que ya estaba borrado;
 *  - es ATOMICO: si falla a mitad no queda nada borrado (el fallo se provoca en el segundo borrado de tarjeta);
 *  - un tema sin tarjetas se borra bien.
 */
class TopicDeleteRoomTest {

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
        TopicRepositoryImpl(db.topicDao(), flashcardDao, RoomTransactionRunner(db), fixedClock)

    private fun topicToDelete(id: String = "t1", deckId: String = "d1") =
        Topic(id = id, deckId = deckId, name = id, createdAt = ms(1), updatedAt = ms(1))

    private suspend fun deck(id: String) = db.deckDao().addDeck(
        DeckEntity(id = id, name = id, createdAt = ms(1), updatedAt = ms(1), description = null,
            lastSyncedAt = ms(1), userId = "u")
    )

    private suspend fun topic(id: String, deckId: String) = db.topicDao().addTopic(
        TopicEntity(id = id, deckId = deckId, name = id, createdAt = ms(1), updatedAt = ms(1),
            lastSyncedAt = ms(1), userId = "u")
    )

    private suspend fun card(id: String, deckId: String, topicId: String?, deletedAt: Long? = null) =
        db.flashcardDao().addFlashcard(
            FlashcardEntity(id = id, deckId = deckId, topicId = topicId, question = "Q", answer = "A",
                cardType = CardType.FLIP, createdAt = ms(1), updatedAt = ms(deletedAt ?: 1),
                deletedAt = deletedAt?.let { ms(it) }, lastSyncedAt = ms(1), userId = "u")
        )

    private suspend fun seed() {
        deck("d1"); deck("d2")
        topic("t1", "d1")        // el que se borra: 2 tarjetas vivas + 1 ya borrada
        topic("t2", "d1")        // otro tema del mismo mazo
        topic("t-empty", "d1")   // tema sin tarjetas
        topic("t3", "d2")        // tema de otro mazo
        card("c1", "d1", "t1"); card("c2", "d1", "t1"); card("c-old", "d1", "t1", deletedAt = 600)
        card("c3", "d1", "t2")
        card("c4", "d1", null)   // sin tema
        card("c5", "d2", "t3")
    }

    private suspend fun topicRow(id: String) = db.topicDao().getTopicByIdIncludingDeleted(id)!!
    private suspend fun cardRow(id: String) = db.flashcardDao().getFlashcardByIdIncludingDeleted(id)!!

    private suspend fun assertUntouched(vararg ids: String) {
        for (id in ids) {
            val row = if (id.startsWith("t")) topicRow(id).let { Triple(it.deletedAt, it.updatedAt, it.lastSyncedAt) }
            else cardRow(id).let { Triple(it.deletedAt, it.updatedAt, it.lastSyncedAt) }
            assertEquals(Triple(null, ms(1), ms(1)), row, "$id debe seguir intacta")
        }
    }

    // --- sin fallo ---------------------------------------------------------------------------------------------------

    @Test
    fun marca_el_tema_y_todas_sus_tarjetas_vivas_con_el_mismo_now() = runBlocking<Unit> {
        repository().deleteTopic(topicToDelete())

        for ((name, row) in listOf(
            "t1" to topicRow("t1").let { it.deletedAt to it.updatedAt },
            "c1" to cardRow("c1").let { it.deletedAt to it.updatedAt },
            "c2" to cardRow("c2").let { it.deletedAt to it.updatedAt }
        )) {
            assertEquals(now to now, row, "$name: deletedAt = updatedAt = now")
        }
        // Siguen "sucias" para el push (updatedAt > lastSyncedAt): se suben como un unico cambio.
        assertEquals(ms(1), topicRow("t1").lastSyncedAt)
        assertEquals(ms(1), cardRow("c1").lastSyncedAt)
    }

    @Test
    fun no_toca_tarjetas_de_otros_temas_ni_sin_tema_ni_de_otros_mazos() = runBlocking<Unit> {
        repository().deleteTopic(topicToDelete())

        assertUntouched("t2", "t-empty", "t3", "c3", "c4", "c5")
    }

    @Test
    fun lo_que_ya_estaba_borrado_conserva_su_marca() = runBlocking<Unit> {
        repository().deleteTopic(topicToDelete())

        assertEquals(ms(600), cardRow("c-old").deletedAt)
        assertEquals(ms(600), cardRow("c-old").updatedAt)
    }

    @Test
    fun un_tema_sin_tarjetas_se_borra_bien() = runBlocking<Unit> {
        repository().deleteTopic(topicToDelete(id = "t-empty"))

        assertEquals(now, topicRow("t-empty").deletedAt)
        assertEquals(now, topicRow("t-empty").updatedAt)
        assertUntouched("t1", "t2", "t3", "c1", "c2", "c3", "c4", "c5")
    }

    @Test
    fun el_tema_borrado_desaparece_de_las_lecturas_normales_y_sus_tarjetas_de_estudio() = runBlocking<Unit> {
        repository().deleteTopic(topicToDelete())

        assertNull(db.topicDao().getTopicById("t1"))
        val due = db.flashcardDao().getAllDueFlashcards(ms(99_999)).map { it.id }.toSet()
        assertEquals(setOf("c3", "c4", "c5"), due, "c1 y c2 ya no cuentan para estudio ni para Home")
    }

    // --- con fallo: atomico ------------------------------------------------------------------------------------------

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
        assertFailsWith<IllegalStateException> { repository(failingOnSecondCardDelete()).deleteTopic(topicToDelete()) }

        // Ni el tema, ni la tarjeta que SI llego a marcarse antes del fallo: todo revertido.
        assertUntouched("t1", "c1", "c2", "t2", "t-empty", "t3", "c3", "c4", "c5")
        assertEquals(ms(600), cardRow("c-old").deletedAt)
    }

    @Test
    fun tras_un_fallo_revertido_se_puede_borrar_el_tema_normalmente() = runBlocking<Unit> {
        assertFailsWith<IllegalStateException> { repository(failingOnSecondCardDelete()).deleteTopic(topicToDelete()) }

        repository().deleteTopic(topicToDelete())

        assertEquals(now, topicRow("t1").deletedAt)
        assertEquals(now, cardRow("c1").deletedAt)
        assertEquals(now, cardRow("c2").deletedAt)
    }
}
