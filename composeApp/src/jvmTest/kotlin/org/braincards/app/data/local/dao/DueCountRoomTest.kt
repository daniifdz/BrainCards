package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.repository.DueCardsObserverImpl
import org.braincards.app.domain.models.CardType
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El contador de Home (FlashcardDao.observeDueCount) contra una Room real en memoria, con foreign_keys = ON: MISMO
 * criterio que la sesion de estudio con StudyScope.All (FlashcardDao.getAllDueFlashcards), y reactivo.
 */
class DueCountRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var observer: DueCardsObserverImpl

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)
    private val now = ms(10_000)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
        observer = DueCardsObserverImpl(db.flashcardDao())
        runBlocking {
            db.deckDao().addDeck(
                DeckEntity(id = "d1", name = "d1", createdAt = ms(1), updatedAt = ms(1), description = null,
                    deletedAt = null, lastSyncedAt = null, userId = null)
            )
        }
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun card(id: String, nextReview: Long?, deleted: Boolean = false) {
        db.flashcardDao().addFlashcard(
            FlashcardEntity(id = id, deckId = "d1", question = "Q", answer = "A", cardType = CardType.FLIP,
                createdAt = ms(1), updatedAt = ms(1), deletedAt = if (deleted) ms(2) else null)
        )
        if (nextReview != null) {
            db.cardSchedulingDao().insert(
                CardSchedulingEntity(cardId = id, easeFactor = 2.5, interval = 1, repetitions = 1,
                    nextReview = ms(nextReview), updatedAt = ms(1), userId = null, lastSyncedAt = null)
            )
        }
    }

    /** Siembra los casos que distinguen cada trozo del criterio. */
    private suspend fun seedMixed() {
        card("sin-scheduling", nextReview = null)        // nunca repasada: vencida (COALESCE a 0)
        card("vencida", nextReview = 5_000)              // pasada: vencida
        card("justo-ahora", nextReview = 10_000)         // nextReview == now: vencida (<=)
        card("futura", nextReview = 20_000)              // no vencida
        card("borrada-vencida", nextReview = 5_000, deleted = true)  // borrada logicamente: no cuenta
        card("borrada-sin-scheduling", nextReview = null, deleted = true)
    }

    private suspend fun count(at: Instant) = observer.observeDueCount(flowOf(at)).first()

    @Test
    fun cuenta_vencidas_sin_scheduling_borde_y_excluye_futuras_y_borradas() = runBlocking<Unit> {
        seedMixed()

        assertEquals(3, count(now))
    }

    @Test
    fun el_recuento_es_identico_a_la_lista_de_la_sesion_de_estudio_para_cualquier_ahora() = runBlocking<Unit> {
        seedMixed()
        // Antes de todo, justo en cada borde, entre medias y despues de todo.
        listOf(0L, 1L, 4_999L, 5_000L, 5_001L, 9_999L, 10_000L, 10_001L, 19_999L, 20_000L, 30_000L).forEach { t ->
            val study = db.flashcardDao().getAllDueFlashcards(ms(t)).size
            assertEquals(study, count(ms(t)), "now=$t: el contador de Home debe coincidir con la sesion de estudio")
        }
    }

    @Test
    fun una_base_vacia_cuenta_cero() = runBlocking<Unit> {
        assertEquals(0, count(now))
    }

    @Test
    fun cambiar_la_hora_cambia_el_recuento_sin_tocar_ninguna_tabla() = runBlocking<Unit> {
        card("c", nextReview = 20_000)

        assertEquals(0, count(now))
        assertEquals(1, count(ms(20_000)))
    }

    @Test
    fun es_reactivo_a_crear_repasar_y_borrar_tarjetas() = runBlocking<Unit> {
        coroutineScope {
            val values = CopyOnWriteArrayList<Int>()
            val job = launch(Dispatchers.Default) { observer.observeDueCount(flowOf(now)).collect { values.add(it) } }
            try {
                awaitLast(values, 0)

                card("a", nextReview = null)                                  // nueva: vencida
                awaitLast(values, 1)

                card("b", nextReview = 1_000)                                 // otra vencida
                awaitLast(values, 2)

                db.cardSchedulingDao().insert(                                // "repasar" b: pasa al futuro
                    CardSchedulingEntity(cardId = "b", easeFactor = 2.5, interval = 6, repetitions = 2,
                        nextReview = ms(99_000), updatedAt = ms(5), userId = null, lastSyncedAt = null)
                )
                awaitLast(values, 1)

                db.flashcardDao().softdeleteFlashcardById("a", ms(6))        // borrar a
                awaitLast(values, 0)
            } finally {
                job.cancel()
            }
        }
    }

    @Test
    fun no_repite_valores_consecutivos_aunque_Room_vuelva_a_emitir() = runBlocking<Unit> {
        coroutineScope {
            val values = CopyOnWriteArrayList<Int>()
            val job = launch(Dispatchers.Default) { observer.observeDueCount(flowOf(now)).collect { values.add(it) } }
            try {
                awaitLast(values, 0)
                card("lejana", nextReview = 99_000)   // escribe en las tablas pero no cambia el recuento (0)
                card("a", nextReview = null)
                awaitLast(values, 1)
                assertEquals(listOf(0, 1), values.toList())
            } finally {
                job.cancel()
            }
        }
    }

    private suspend fun awaitLast(values: List<Int>, expected: Int) {
        withTimeout(5_000) { while (values.lastOrNull() != expected) delay(10) }
    }
}
