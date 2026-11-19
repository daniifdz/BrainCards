package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.room.useWriterConnection
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
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.local.entity.TopicEntity
import org.braincards.app.data.repository.CardSchedulingRepositoryImpl
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.FlashcardRepositoryImpl
import org.braincards.app.data.repository.LocalDataRepositoryImpl
import org.braincards.app.data.repository.PendingChangesObserverImpl
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.PendingStamp
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * PendingChangesObserver (consultas Flow de los 4 DAO) contra una Room real en memoria, con foreign_keys = ON:
 * cuenta lo mismo que getPendingSync de los 4 repositorios + las filas sin dueno, y re-emite al cambiar.
 */
class PendingChangesObserverRoomTest {

    private val me = "user-a"
    private val other = "user-b"
    private lateinit var db: AppDatabase
    private lateinit var observer: PendingChangesObserverImpl

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
        observer = PendingChangesObserverImpl(db.deckDao(), db.topicDao(), db.flashcardDao(), db.cardSchedulingDao())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun deck(id: String, owner: String?, updated: Long, synced: Long?, deleted: Boolean = false) =
        db.deckDao().addDeck(
            DeckEntity(id = id, name = id, createdAt = ms(1), updatedAt = ms(updated), description = null,
                deletedAt = if (deleted) ms(updated) else null, lastSyncedAt = synced?.let { ms(it) }, userId = owner)
        )

    private suspend fun topic(id: String, deckId: String, owner: String?, updated: Long, synced: Long?) =
        db.topicDao().addTopic(
            TopicEntity(id = id, deckId = deckId, name = id, createdAt = ms(1), updatedAt = ms(updated),
                lastSyncedAt = synced?.let { ms(it) }, userId = owner)
        )

    private suspend fun card(id: String, deckId: String, topicId: String?, owner: String?, updated: Long, synced: Long?) =
        db.flashcardDao().addFlashcard(
            FlashcardEntity(id = id, deckId = deckId, topicId = topicId, question = "Q", answer = "A",
                cardType = CardType.FLIP, createdAt = ms(1), updatedAt = ms(updated),
                lastSyncedAt = synced?.let { ms(it) }, userId = owner)
        )

    private suspend fun scheduling(cardId: String, owner: String?, updated: Long, synced: Long?) =
        db.cardSchedulingDao().insert(
            CardSchedulingEntity(cardId = cardId, easeFactor = 2.5, interval = 1, repetitions = 1, nextReview = ms(9_000),
                updatedAt = ms(updated), userId = owner, lastSyncedAt = synced?.let { ms(it) })
        )

    private suspend fun current(userId: String = me): PendingStamp = observer.observe(userId).first()

    /**
     * Suscribe en segundo plano mientras corre [block] y cancela la suscripcion al terminar (si no, el
     * runBlocking del test esperaria para siempre al collector). Le pasa la lista (hilo-segura) de huellas
     * emitidas, en orden.
     */
    private suspend fun collecting(userId: String = me, block: suspend (List<PendingStamp>) -> Unit) {
        coroutineScope {
            val values = CopyOnWriteArrayList<PendingStamp>()
            val job = launch(Dispatchers.Default) { observer.observe(userId).collect { values.add(it) } }
            try {
                block(values)
            } finally {
                job.cancel()
            }
        }
    }

    private suspend fun awaitLast(values: List<PendingStamp>, predicate: (PendingStamp) -> Boolean) {
        withTimeout(5_000) { while (values.lastOrNull()?.let(predicate) != true) delay(10) }
    }

    @Test
    fun foreign_keys_esta_activo() = runBlocking {
        val pragma = db.useWriterConnection { tx -> tx.usePrepared("PRAGMA foreign_keys") { st -> st.step(); st.getLong(0) } }
        assertEquals(1L, pragma)
    }

    @Test
    fun una_bd_vacia_no_tiene_nada_pendiente() = runBlocking {
        assertEquals(PendingStamp(0, 0), current())
    }

    @Test
    fun cuenta_las_pendientes_propias_y_el_mas_reciente_de_las_cuatro_tablas() = runBlocking {
        deck("d1", me, updated = 100, synced = null)
        topic("t1", "d1", me, updated = 200, synced = 150)
        card("c1", "d1", "t1", me, updated = 300, synced = null)
        scheduling("c1", me, updated = 400, synced = null) // la flashcard aun no se ha subido: NO cuenta

        val stamp = current()

        assertEquals(PendingStamp(count = 3, newestUpdatedAtMillis = 300), stamp)
    }

    @Test
    fun las_filas_limpias_y_las_de_otro_usuario_no_cuentan() = runBlocking {
        deck("d1", me, updated = 100, synced = 100)            // limpia (updatedAt == lastSyncedAt)
        deck("dx", other, updated = 100, synced = null)
        topic("t1", "d1", me, updated = 100, synced = 100)
        card("c1", "d1", null, me, updated = 100, synced = 100)
        scheduling("c1", me, updated = 100, synced = 100)

        assertEquals(PendingStamp(0, 0), current())
    }

    @Test
    fun las_filas_sin_dueno_cuentan_en_las_cuatro_tablas() = runBlocking {
        deck("d1", null, updated = 10, synced = null)
        topic("t1", "d1", null, updated = 20, synced = null)
        card("c1", "d1", "t1", null, updated = 30, synced = null)
        scheduling("c1", null, updated = 40, synced = null)

        assertEquals(PendingStamp(count = 4, newestUpdatedAtMillis = 40), current())
    }

    @Test
    fun un_borrado_logico_pendiente_cuenta() = runBlocking {
        deck("d1", me, updated = 500, synced = 100, deleted = true)

        assertEquals(PendingStamp(1, 500), current())
    }

    @Test
    fun un_topic_y_una_flashcard_cuyo_padre_es_de_otro_dueno_no_cuentan() = runBlocking {
        deck("d-otro", other, updated = 1, synced = 1)
        topic("t1", "d-otro", me, updated = 100, synced = null)
        card("c1", "d-otro", null, me, updated = 100, synced = null)
        deck("d-mio", me, updated = 1, synced = 1)
        topic("t-otro", "d-mio", other, updated = 1, synced = 1)
        card("c2", "d-mio", "t-otro", me, updated = 100, synced = null) // su topic es de otro dueno

        assertEquals(PendingStamp(0, 0), current())
    }

    @Test
    fun cada_usuario_ve_solo_lo_suyo_mas_lo_que_no_tiene_dueno() = runBlocking {
        deck("da", me, updated = 100, synced = null)
        deck("db", other, updated = 200, synced = null)
        deck("dn", null, updated = 300, synced = null)

        assertEquals(PendingStamp(2, 300), current(me))
        assertEquals(PendingStamp(2, 300), current(other))
        assertEquals(PendingStamp(1, 300), current("user-c"))
    }

    @Test
    fun re_emite_cuando_entra_un_cambio_cuando_se_edita_uno_pendiente_y_cuando_se_sube() = runBlocking {
        collecting { values ->
            awaitLast(values) { it == PendingStamp(0, 0) }

            deck("d1", me, updated = 100, synced = null)
            awaitLast(values) { it == PendingStamp(1, 100) }

            // Editar una fila que YA estaba pendiente: el conteo no cambia, el maximo si.
            db.deckDao().addDeck(DeckEntity(id = "d1", name = "editado", createdAt = ms(1), updatedAt = ms(250),
                description = null, lastSyncedAt = null, userId = me))
            awaitLast(values) { it == PendingStamp(1, 250) }

            // Subida: markPushed deja la fila limpia.
            db.deckDao().markDecksPushed(listOf("d1"), ms(300))
            awaitLast(values) { it == PendingStamp(0, 0) }

            // Y nunca dos huellas iguales seguidas.
            values.zipWithNext().forEach { (x, y) -> assertNotEquals(x, y, "emision repetida: $values") }
        }
    }

    @Test
    fun un_cambio_en_una_tabla_que_no_altera_la_huella_no_provoca_emision_repetida() = runBlocking {
        deck("d1", me, updated = 100, synced = null)
        collecting { values ->
            awaitLast(values) { it == PendingStamp(1, 100) }

            deck("dx", other, updated = 999, synced = null)      // invalida decks, pero la huella de `me` es la misma
            deck("dy", me, updated = 400, synced = null)         // marcador: esta si cambia la huella
            awaitLast(values) { it == PendingStamp(2, 400) }

            assertEquals(listOf(PendingStamp(1, 100), PendingStamp(2, 400)), values.toList())
        }
    }

    @Test
    fun propiedad_la_huella_coincide_con_getPendingSync_mas_countUnowned_en_bds_aleatorias() = runBlocking {
        val decksRepo = DeckRepositoryImpl(db.deckDao(), db.topicDao(), db.flashcardDao(), RoomTransactionRunner(db))
        val topicsRepo = TopicRepositoryImpl(db.topicDao(), db.flashcardDao(), RoomTransactionRunner(db))
        val cardsRepo = FlashcardRepositoryImpl(db.flashcardDao())
        val schedRepo = CardSchedulingRepositoryImpl(db.cardSchedulingDao(), RoomTransactionRunner(db))
        val local = LocalDataRepositoryImpl(db.deckDao(), db.topicDao(), db.flashcardDao(), db.cardSchedulingDao(),
            db.reviewLogDao(), RoomTransactionRunner(db))

        val owners = listOf(me, other, null)
        for (seed in 1..25) {
            local.wipeAll()
            val rnd = Random(seed)
            fun owner() = owners[rnd.nextInt(owners.size)]
            fun updated() = 100L + rnd.nextInt(50)
            // null = nunca subida; == updatedAt = limpia; > updatedAt = limpia (subida despues de editar)
            fun synced(upd: Long): Long? = when (rnd.nextInt(3)) { 0 -> null; 1 -> upd; else -> upd + 1 + rnd.nextInt(10) }

            val deckIds = (1..rnd.nextInt(1, 5)).map { "d$it" }
            deckIds.forEach { id -> val u = updated(); deck(id, owner(), u, synced(u), deleted = rnd.nextInt(5) == 0) }
            val topicIds = (1..rnd.nextInt(0, 6)).map { "t$it" }
            topicIds.forEach { id -> val u = updated(); topic(id, deckIds.random(rnd), owner(), u, synced(u)) }
            val cardIds = (1..rnd.nextInt(0, 7)).map { "c$it" }
            cardIds.forEach { id ->
                val u = updated()
                card(id, deckIds.random(rnd), topicIds.randomOrNull(rnd), owner(), u, synced(u))
            }
            cardIds.filter { rnd.nextBoolean() }.forEach { id -> val u = updated(); scheduling(id, owner(), u, synced(u)) }

            for (user in listOf(me, other)) {
                val expected = decksRepo.getPendingSync(user).size + topicsRepo.getPendingSync(user).size +
                    cardsRepo.getPendingSync(user).size + schedRepo.getPendingSync(user).size + local.countUnownedRows()

                assertEquals(expected, current(user).count, "seed=$seed user=$user")
            }
        }
    }
}
