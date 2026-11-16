package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.repository.DeckSummariesObserverImpl
import org.braincards.app.domain.models.CardType
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La consulta agregada de la Lista de mazos (DeckDao.observeDeckSummaries) contra una Room real en memoria:
 * una sola consulta con LEFT JOIN + GROUP BY, tarjetas borradas fuera del recuento, mazos sin tarjetas incluidos.
 */
class DeckSummariesRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var observer: DeckSummariesObserverImpl

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
        observer = DeckSummariesObserverImpl(db.deckDao())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun deck(id: String, deleted: Boolean = false) {
        db.deckDao().addDeck(
            DeckEntity(id = id, name = id, createdAt = ms(1), updatedAt = ms(1), description = null,
                deletedAt = if (deleted) ms(2) else null)
        )
    }

    private suspend fun card(id: String, deckId: String, deleted: Boolean = false) {
        db.flashcardDao().addFlashcard(
            FlashcardEntity(id = id, deckId = deckId, question = "Q", answer = "A", cardType = CardType.FLIP,
                createdAt = ms(1), updatedAt = ms(1), deletedAt = if (deleted) ms(2) else null)
        )
    }

    private suspend fun counts(): Map<String, Int> =
        observer.observeDeckSummaries().first().associate { it.deck.id to it.cardCount }

    @Test
    fun un_mazo_sin_tarjetas_aparece_con_cero() = runBlocking<Unit> {
        deck("vacio")

        assertEquals(mapOf("vacio" to 0), counts())
    }

    @Test
    fun cuenta_las_tarjetas_de_cada_mazo_sin_mezclarlas() = runBlocking<Unit> {
        deck("a"); deck("b"); deck("c")
        card("a1", "a"); card("a2", "a"); card("a3", "a")
        card("b1", "b")

        assertEquals(mapOf("a" to 3, "b" to 1, "c" to 0), counts())
    }

    @Test
    fun las_tarjetas_borradas_no_cuentan_pero_el_mazo_con_solo_borradas_sigue_apareciendo_con_cero() = runBlocking<Unit> {
        deck("a"); deck("solo-borradas")
        card("a1", "a"); card("a2", "a", deleted = true)
        card("x1", "solo-borradas", deleted = true)

        assertEquals(mapOf("a" to 1, "solo-borradas" to 0), counts())
    }

    @Test
    fun un_mazo_borrado_no_aparece_ni_aunque_tenga_tarjetas_vivas() = runBlocking<Unit> {
        deck("vivo"); deck("borrado", deleted = true)
        card("v1", "vivo"); card("b1", "borrado")

        assertEquals(mapOf("vivo" to 1), counts())
    }

    @Test
    fun el_recuento_es_reactivo_al_anadir_y_borrar_tarjetas() = runBlocking<Unit> {
        deck("a")
        val seen = CopyOnWriteArrayList<Int>()

        suspend fun waitUntil(condition: () -> Boolean) {
            while (!condition()) delay(10)
        }

        withTimeout(10_000) {
            coroutineScope {
                val job = launch(Dispatchers.Default) {
                    observer.observeDeckSummaries().collect { rows -> seen += rows.single().cardCount }
                }
                waitUntil { seen.isNotEmpty() }
                assertEquals(0, seen.last())

                card("a1", "a")
                waitUntil { seen.last() == 1 }

                db.flashcardDao().softdeleteFlashcardById("a1", ms(5))
                waitUntil { seen.last() == 0 }

                job.cancel()
            }
        }
        assertTrue(seen.containsAll(listOf(0, 1)))
    }
}
