package org.braincards.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.SyncTable
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * El almacen sobre un DataStore REAL (fichero .preferences_pb temporal): escrituras concurrentes de
 * distintas tablas desde hilos distintos no se pisan, y lo guardado sobrevive a reabrir el fichero.
 */
class DataStorePullWatermarkStoreFileTest {

    private val user = "user-a"

    private fun withStore(block: suspend (DataStorePullWatermarkStore, suspend () -> DataStorePullWatermarkStore) -> Unit) {
        val dir = Files.createTempDirectory("braincards-watermark-test").toFile()
        val file = File(dir, "test.preferences_pb")
        val scopes = mutableListOf<CoroutineScope>()
        fun open(): DataStorePullWatermarkStore {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
            return DataStorePullWatermarkStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        }
        // Un DataStore por fichero y proceso: para reabrir hay que cerrar (cancelar y esperar) el anterior.
        suspend fun reopen(): DataStorePullWatermarkStore {
            scopes.forEach { it.coroutineContext[Job]!!.cancelAndJoin() }
            scopes.clear()
            return open()
        }
        try {
            runBlocking { block(open(), ::reopen) }
        } finally {
            scopes.forEach { it.cancel() }
            dir.deleteRecursively()
        }
    }

    @Test
    fun `escrituras concurrentes de las cuatro tablas no se pisan`() = withStore { store, _ ->
        val cursors = SyncTable.entries.mapIndexed { index, table ->
            table to PullCursor(Instant.parse("2026-10-22T10:00:0${index}Z"), "id-$index")
        }

        coroutineScope {
            cursors.map { (table, cursor) ->
                async(Dispatchers.Default) { store.saveCursor(user, table, cursor) }
            }.awaitAll()
        }

        assertEquals(cursors.toMap(), store.load(user)!!.cursors)
    }

    @Test
    fun `lo guardado se lee al reabrir el fichero`() = withStore { store, reopen ->
        store.saveCursor(user, SyncTable.DECKS, PullCursor(Instant.parse("2026-10-22T10:00:00.123456Z"), "deck-1"))
        store.saveFullPullAt(user, Instant.parse("2026-10-20T08:00:00Z"))

        val loaded = assertNotNull(reopen().load(user))

        assertEquals(PullCursor(Instant.parse("2026-10-22T10:00:00.123456Z"), "deck-1"), loaded.cursors[SyncTable.DECKS])
        assertEquals(Instant.parse("2026-10-20T08:00:00Z"), loaded.lastFullPullAt)
    }
}
