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
import org.braincards.app.data.repository.LocalDataRepositoryImpl
import org.braincards.app.domain.models.CardType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Limpieza total de la BD local al cerrar sesion, contra una Room real en memoria (el borrado
 * en orden con foreign_keys activo y la atomicidad estan en el motor SQL, un fake no los cubre).
 */
class LocalDataRepositoryRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: LocalDataRepositoryImpl

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    // Tablas de usuario que wipeAll() debe vaciar. Si AppDatabase gana una tabla, el test de
    // abajo falla con su nombre: hay que anadirla a wipeAll(), a seedMixedOwners() y aqui.
    private val expectedTables = setOf("decks", "topics", "flashcards", "card_scheduling", "reviewlog")

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = LocalDataRepositoryImpl(
            deckDao = db.deckDao(),
            topicDao = db.topicDao(),
            flashcardDao = db.flashcardDao(),
            cardSchedulingDao = db.cardSchedulingDao(),
            reviewLogDao = db.reviewLogDao(),
            transactionRunner = RoomTransactionRunner(db)
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    /**
     * Tres filas por tabla: una de la cuenta A, una de la cuenta B y una sin dueno (userId NULL).
     * La fila sin dueno de decks es ademas un tombstone (deletedAt) para comprobar que el
     * borrado es fisico y no solo de las filas vivas. Sin dueno: 4 filas en las 4 tablas
     * sincronizables (reviewlog no cuenta).
     */
    private suspend fun seedMixedOwners() {
        val owners = listOf("deck-a" to "user-a", "deck-b" to "user-b", "deck-n" to null)
        owners.forEach { (deckId, userId) ->
            val suffix = deckId.removePrefix("deck-")
            db.deckDao().addDeck(
                DeckEntity(
                    id = deckId, name = "D $suffix", createdAt = ms(1), updatedAt = ms(1), description = null,
                    deletedAt = if (userId == null) ms(2) else null, userId = userId
                )
            )
            db.topicDao().addTopic(
                TopicEntity(id = "topic-$suffix", deckId = deckId, name = "T", createdAt = ms(1), updatedAt = ms(1), userId = userId)
            )
            db.flashcardDao().addFlashcard(
                FlashcardEntity(
                    id = "card-$suffix", deckId = deckId, topicId = "topic-$suffix", question = "Q", answer = "A",
                    cardType = CardType.FLIP, createdAt = ms(1), updatedAt = ms(1), userId = userId
                )
            )
            db.cardSchedulingDao().insert(
                CardSchedulingEntity(
                    cardId = "card-$suffix", easeFactor = 2.5, interval = 1, repetitions = 1,
                    nextReview = ms(5000), updatedAt = ms(1), userId = userId, lastSyncedAt = null
                )
            )
            db.reviewLogDao().addReviewLog(
                ReviewLogEntity(
                    id = "log-$suffix", cardId = "card-$suffix", timestamp = ms(1), grade = 3,
                    previousInterval = 0, newInterval = 1, userId = userId
                )
            )
        }
    }

    /** Todas las tablas reales de la BD menos las internas de SQLite/Room (y nada mas). */
    private suspend fun userTableNames(): Set<String> = db.useWriterConnection { transactor ->
        transactor.usePrepared("SELECT name FROM sqlite_master WHERE type = 'table'") { stmt ->
            val names = mutableSetOf<String>()
            while (stmt.step()) names.add(stmt.getText(0))
            names
        }
    }.filterNot { it.startsWith("sqlite_") || it == "room_master_table" || it == "android_metadata" }.toSet()

    private suspend fun countRows(table: String): Long = db.useWriterConnection { transactor ->
        transactor.usePrepared("SELECT COUNT(*) FROM \"$table\"") { stmt ->
            stmt.step()
            stmt.getLong(0)
        }
    }

    private suspend fun rowCountsByTable(): Map<String, Long> =
        userTableNames().associateWith { countRows(it) }

    @Test
    fun las_tablas_de_usuario_de_la_bd_son_exactamente_las_que_el_wipe_conoce() = runBlocking {
        val actual = userTableNames()
        assertEquals(
            expectedTables,
            actual,
            "Hay una tabla nueva (o falta una) en AppDatabase: ${actual - expectedTables} sobran, " +
                "${expectedTables - actual} faltan. Anadir la tabla a LocalDataRepositoryImpl.wipeAll(), " +
                "a seedMixedOwners() y a expectedTables en este test."
        )
    }

    @Test
    fun wipeAll_no_deja_ninguna_fila_en_ninguna_tabla_con_filas_de_A_de_B_y_sin_dueno() = runBlocking {
        seedMixedOwners()
        // La siembra no es vacua: cada tabla tiene 3 filas antes de borrar.
        assertEquals(expectedTables.associateWith { 3L }, rowCountsByTable())
        // foreign_keys activo: si el orden de borrado fuera hijos <- padres, esto fallaria.
        val fk = db.useWriterConnection { transactor ->
            transactor.usePrepared("PRAGMA foreign_keys") { stmt -> stmt.step(); stmt.getLong(0) }
        }
        assertEquals(1L, fk)

        repository.wipeAll()

        // Se recorre sqlite_master (no una lista fija) para no olvidar ninguna tabla.
        val tables = userTableNames()
        assertTrue(tables.isNotEmpty())
        tables.forEach { table -> assertEquals(0L, countRows(table), "La tabla $table no quedo vacia") }
    }

    @Test
    fun wipeAll_sobre_una_bd_vacia_no_falla() = runBlocking {
        repository.wipeAll()

        userTableNames().forEach { table -> assertEquals(0L, countRows(table)) }
    }

    @Test
    fun wipeAll_es_atomico_si_falla_el_ultimo_borrado_no_se_pierde_ninguna_fila() = runBlocking {
        seedMixedOwners()
        val before = rowCountsByTable()
        // decks es la ULTIMA tabla que borra wipeAll(): cuando este trigger aborta, los DELETE
        // de card_scheduling, reviewlog, flashcards y topics ya se han ejecutado dentro de la
        // transaccion, asi que el test solo pasa si Room los revierte.
        db.useWriterConnection { transactor ->
            transactor.usePrepared(
                "CREATE TRIGGER fail_decks_delete BEFORE DELETE ON decks " +
                    "BEGIN SELECT RAISE(ABORT, 'fallo simulado'); END"
            ) { stmt -> stmt.step() }
        }

        assertFails { repository.wipeAll() }

        assertEquals(before, rowCountsByTable())
    }

    @Test
    fun countUnownedRows_cuenta_solo_las_filas_sin_dueno_de_las_cuatro_tablas_sincronizables() = runBlocking {
        seedMixedOwners()

        // 1 por tabla (decks incluye el tombstone) x 4 tablas; reviewlog (tambien con una
        // fila sin dueno) no cuenta porque no se sincroniza.
        assertEquals(4, repository.countUnownedRows())
    }

    @Test
    fun countUnownedRows_es_cero_con_la_bd_vacia_y_tras_wipeAll() = runBlocking {
        assertEquals(0, repository.countUnownedRows())

        seedMixedOwners()
        repository.wipeAll()

        assertEquals(0, repository.countUnownedRows())
    }
}
