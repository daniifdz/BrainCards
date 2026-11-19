package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.repository.CardSchedulingRepositoryImpl
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.CardType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * SQL de sync de card_scheduling y transaccion de applyRemotePull contra una Room real en
 * memoria (la logica esta en las consultas / en el lock de escritura, un fake no la cubriria).
 */
class CardSchedulingDaoSyncTest {

    private lateinit var db: AppDatabase
    private val dao get() = db.cardSchedulingDao()

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        runBlocking {
            db.deckDao().addDeck(DeckEntity(id = "deck-1", name = "D", createdAt = ms(1), updatedAt = ms(1), description = null))
        }
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun addCard(
        id: String,
        userId: String? = null,
        lastSyncedAt: Instant? = null,
        deletedAt: Instant? = null
    ) {
        db.flashcardDao().addFlashcard(
            FlashcardEntity(
                id = id, deckId = "deck-1", question = "Q", answer = "A", cardType = CardType.FLIP,
                createdAt = ms(1), updatedAt = ms(1), deletedAt = deletedAt, lastSyncedAt = lastSyncedAt, userId = userId
            )
        )
    }

    private fun entity(
        cardId: String,
        userId: String? = null,
        updatedAt: Long = 1000,
        lastSyncedAt: Long? = null
    ) = CardSchedulingEntity(
        cardId = cardId, easeFactor = 2.5, interval = 1, repetitions = 1,
        nextReview = ms(5000), updatedAt = ms(updatedAt), userId = userId, lastSyncedAt = lastSyncedAt?.let { ms(it) }
    )

    private fun domain(cardId: String, updatedAt: Long, repetitions: Int = 1) = CardScheduling(
        cardId = cardId, easeFactor = 2.5, interval = 1, repetitions = repetitions,
        nextReview = ms(5000), updatedAt = ms(updatedAt), userId = "user-1", lastSyncedAt = null
    )

    @Test
    fun foreign_keys_esta_activo_e_insertar_scheduling_con_cardId_inexistente_falla() = runBlocking {
        val pragma = db.useWriterConnection { transactor ->
            transactor.usePrepared("PRAGMA foreign_keys") { stmt ->
                stmt.step()
                stmt.getLong(0)
            }
        }
        assertEquals(1L, pragma)

        assertFailsWith<SQLiteException> { dao.insert(entity("no-existe")) }
        Unit
    }

    @Test
    fun claim_solo_reclama_huerfanos_cuya_flashcard_es_del_usuario() = runBlocking {
        addCard("mia", userId = "user-1")
        addCard("de-otro", userId = "user-2")
        addCard("sin-dueno", userId = null)
        dao.insert(entity("mia"))
        dao.insert(entity("de-otro"))
        dao.insert(entity("sin-dueno"))

        val claimed = dao.claimOrphanedScheduling("user-1")

        assertEquals(1, claimed)
        assertEquals("user-1", dao.getByCardId("mia")!!.userId)
        assertNull(dao.getByCardId("de-otro")!!.userId)
        assertNull(dao.getByCardId("sin-dueno")!!.userId)
    }

    @Test
    fun pendientes_exige_flashcard_subida_y_del_mismo_dueno() = runBlocking {
        addCard("ok", userId = "user-1", lastSyncedAt = ms(10))
        addCard("tombstone", userId = "user-1", lastSyncedAt = ms(10), deletedAt = ms(20))
        addCard("flashcard-sin-subir", userId = "user-1", lastSyncedAt = null)
        addCard("flashcard-de-otro", userId = "user-2", lastSyncedAt = ms(10))
        dao.insert(entity("ok", userId = "user-1"))
        dao.insert(entity("tombstone", userId = "user-1"))
        dao.insert(entity("flashcard-sin-subir", userId = "user-1"))
        // scheduling dice user-1 pero su flashcard es de user-2: la RLS lo rechazaria
        dao.insert(entity("flashcard-de-otro", userId = "user-1"))

        val pending = dao.getPendingSyncScheduling("user-1").map { it.cardId }.toSet()

        assertEquals(setOf("ok", "tombstone"), pending)
    }

    @Test
    fun pendientes_excluye_lo_ya_sincronizado_y_lo_de_otros_usuarios_e_incluye_lo_modificado() = runBlocking {
        listOf("nueva", "modificada", "sincronizada", "ajena").forEach {
            addCard(it, userId = if (it == "ajena") "user-2" else "user-1", lastSyncedAt = ms(10))
        }
        dao.insert(entity("nueva", userId = "user-1", updatedAt = 1000, lastSyncedAt = null))
        dao.insert(entity("modificada", userId = "user-1", updatedAt = 3000, lastSyncedAt = 2000))
        dao.insert(entity("sincronizada", userId = "user-1", updatedAt = 2000, lastSyncedAt = 2000))
        dao.insert(entity("ajena", userId = "user-2"))

        val pending = dao.getPendingSyncScheduling("user-1").map { it.cardId }.toSet()

        assertEquals(setOf("nueva", "modificada"), pending)
    }

    @Test
    fun markPushed_y_release() = runBlocking {
        addCard("a", userId = "user-1")
        addCard("b", userId = "user-1")
        dao.insert(entity("a", userId = "user-1"))
        dao.insert(entity("b", userId = "user-2"))

        dao.markSchedulingPushed(listOf("a"), ms(9000))
        assertEquals(ms(9000), dao.getByCardId("a")!!.lastSyncedAt)
        assertNull(dao.getByCardId("b")!!.lastSyncedAt)

        assertEquals(1, dao.releaseSchedulingFromUser("user-1"))
        assertNull(dao.getByCardId("a")!!.userId)
        assertNull(dao.getByCardId("a")!!.lastSyncedAt)
        assertEquals("user-2", dao.getByCardId("b")!!.userId)
    }

    // El motor captura `now` ANTES de leer los pendientes y llama a markPushed(ids, now). Una
    // fila repasada durante el push tiene updatedAt > now: debe SEGUIR pendiente aunque su id
    // vaya en la lista de marcados (updatedAt > lastSyncedAt).
    @Test
    fun markPushed_con_now_deja_de_ser_pendiente_la_fila_anterior_y_sigue_pendiente_la_repasada_durante_el_push() = runBlocking {
        addCard("antes", userId = "user-1", lastSyncedAt = ms(10))
        addCard("durante", userId = "user-1", lastSyncedAt = ms(10))
        val now = ms(2000)
        dao.insert(entity("antes", userId = "user-1", updatedAt = 1000)) // anterior a now
        dao.insert(entity("durante", userId = "user-1", updatedAt = 3000)) // repaso posterior a now
        assertEquals(setOf("antes", "durante"), dao.getPendingSyncScheduling("user-1").map { it.cardId }.toSet())

        dao.markSchedulingPushed(listOf("antes", "durante"), now)

        assertEquals(setOf("durante"), dao.getPendingSyncScheduling("user-1").map { it.cardId }.toSet())
        assertEquals(now, dao.getByCardId("antes")!!.lastSyncedAt)
        assertEquals(now, dao.getByCardId("durante")!!.lastSyncedAt)
    }

    @Test
    fun getExistingFlashcardIds_incluye_las_borradas_logicamente() = runBlocking {
        addCard("viva")
        addCard("borrada", deletedAt = ms(5))

        val ids = dao.getExistingFlashcardIds(listOf("viva", "borrada", "no-existe")).toSet()

        assertEquals(setOf("viva", "borrada"), ids)
    }

    private fun repository() = CardSchedulingRepositoryImpl(dao, RoomTransactionRunner(db))

    @Test
    fun applyRemotePull_descarta_huerfanas_sin_abortar_el_lote_y_trocea_los_ids() = runBlocking {
        // > 500 ids para forzar el troceo del guard (limite de variables de SQLite).
        for (i in 1..1200) addCard("c$i")
        val remote = (1..1200).map { domain("c$it", updatedAt = 1000) } +
            listOf(domain("huerfana-1", 1000), domain("huerfana-2", 1000))

        val applied = repository().applyRemotePull(remote)

        assertEquals(1200, applied)
        assertNull(dao.getByCardId("huerfana-1"))
        assertEquals(ms(1000), dao.getByCardId("c1200")!!.lastSyncedAt)
    }

    @Test
    fun llamar_al_dao_dentro_de_inTransaction_no_se_bloquea() = runBlocking {
        addCard("c")
        val runner = RoomTransactionRunner(db)

        val result = withTimeout(10_000) {
            runner.inTransaction {
                dao.insert(entity("c", updatedAt = 1000))
                val read = dao.getByCardId("c")
                dao.insertAllScheduling(listOf(entity("c", updatedAt = 2000)))
                dao.getExistingFlashcardIds(listOf("c")) to read
            }
        }

        assertEquals(listOf("c"), result.first)
        assertEquals(ms(1000), result.second!!.updatedAt)
        assertEquals(ms(2000), dao.getByCardId("c")!!.updatedAt)
    }

    // Un repaso (upsert) lanzado mientras applyRemotePull esta en marcha, ENTRE su lectura local
    // y su escritura, no se puede pisar con el dato remoto mas viejo: el lock de escritura de la
    // transaccion hace que el upsert espere y se aplique despues (gana el ultimo repaso).
    @Test
    fun un_upsert_concurrente_a_applyRemotePull_no_se_pierde() = runBlocking {
        addCard("c")
        dao.insert(entity("c", userId = "user-1", updatedAt = 1000))

        val reachedRead = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val gatedDao = object : CardSchedulingDao by dao {
            override suspend fun getByCardId(cardId: String): CardSchedulingEntity? {
                val local = dao.getByCardId(cardId)
                reachedRead.complete(Unit)
                gate.await() // el pull ya leyo el local (updatedAt 1000) y aun no ha escrito
                return local
            }
        }
        val pullingRepository = CardSchedulingRepositoryImpl(gatedDao, RoomTransactionRunner(db))
        val plainRepository = repository()

        withTimeout(20_000) {
            val pull = async(Dispatchers.IO) {
                pullingRepository.applyRemotePull(listOf(domain("c", updatedAt = 2000, repetitions = 2)))
            }
            reachedRead.await()
            // Repaso del usuario, mas nuevo que el dato remoto, mientras el pull esta a mitad.
            val review = launch(Dispatchers.IO) {
                plainRepository.upsert(domain("c", updatedAt = 3000, repetitions = 9))
            }
            kotlinx.coroutines.delay(300)
            gate.complete(Unit)
            pull.await()
            review.join()
        }

        val final = dao.getByCardId("c")!!
        assertEquals(ms(3000), final.updatedAt)
        assertEquals(9, final.repetitions)
    }
}
