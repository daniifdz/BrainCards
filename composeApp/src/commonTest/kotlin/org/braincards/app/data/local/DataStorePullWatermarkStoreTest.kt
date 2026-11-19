package org.braincards.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.PullWatermarks
import org.braincards.app.domain.models.SyncTable
import org.braincards.app.domain.repository.NoOpPullWatermarkStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Almacen de la marca de agua sobre el DataStore (aqui un fake en memoria, sin disco). */
class DataStorePullWatermarkStoreTest {

    private val userA = "user-a"
    private val userB = "user-b"

    // Nombre real de la clave en disco: si alguien la renombra, las marcas guardadas se pierden.
    private val watermarkKey = stringPreferencesKey("pull_watermark")
    private val themeKey = stringPreferencesKey("app_theme")

    private fun cursor(ts: String, id: String) = PullCursor(Instant.parse(ts), id)

    private val decks = cursor("2026-10-22T10:00:00.123456Z", "deck-9")
    private val topics = cursor("2026-10-22T10:00:01Z", "topic-9")
    private val flashcards = cursor("2026-10-22T10:00:02.5Z", "card-9")
    private val scheduling = cursor("2026-10-22T10:00:03.000001Z", "sched-9")
    private val fullPullAt = Instant.parse("2026-10-20T08:00:00Z")

    @Test
    fun `ida y vuelta de los cuatro cursores y la fecha del ultimo pull completo`() = runBlocking {
        val store = DataStorePullWatermarkStore(FakeSettingsDataStore())

        store.saveCursor(userA, SyncTable.DECKS, decks)
        store.saveCursor(userA, SyncTable.TOPICS, topics)
        store.saveCursor(userA, SyncTable.FLASHCARDS, flashcards)
        store.saveCursor(userA, SyncTable.CARD_SCHEDULING, scheduling)
        store.saveFullPullAt(userA, fullPullAt)

        assertEquals(
            PullWatermarks(
                cursors = mapOf(
                    SyncTable.DECKS to decks,
                    SyncTable.TOPICS to topics,
                    SyncTable.FLASHCARDS to flashcards,
                    SyncTable.CARD_SCHEDULING to scheduling
                ),
                lastFullPullAt = fullPullAt
            ),
            store.load(userA)
        )
    }

    @Test
    fun `sin nada guardado no hay marca`() = runBlocking {
        assertNull(DataStorePullWatermarkStore(FakeSettingsDataStore()).load(userA))
    }

    @Test
    fun `se guarda en una sola clave con instantes ISO en UTC con Z y sin tocar otras claves`() = runBlocking {
        val dataStore = FakeSettingsDataStore(preferencesOf(themeKey to "DARK"))
        val store = DataStorePullWatermarkStore(dataStore)

        store.saveCursor(userA, SyncTable.DECKS, decks)
        store.saveFullPullAt(userA, fullPullAt)

        val prefs = dataStore.data.first()
        assertEquals(setOf("pull_watermark", "app_theme"), prefs.asMap().keys.map { it.name }.toSet())
        assertEquals("DARK", prefs[themeKey])
        val json = prefs[watermarkKey]!!
        assertTrue("2026-10-22T10:00:00.123456Z" in json, "cursor con Z y microsegundos exactos: $json")
        assertTrue("2026-10-20T08:00:00Z" in json, "fecha del ultimo pull completo: $json")
        assertFalse("+00:00" in json, "nunca +00:00: $json")
    }

    @Test
    fun `actualizar el cursor de una tabla conserva el de las demas y la fecha de pull completo`() = runBlocking {
        val store = DataStorePullWatermarkStore(FakeSettingsDataStore())
        store.saveCursor(userA, SyncTable.DECKS, decks)
        store.saveCursor(userA, SyncTable.TOPICS, topics)
        store.saveFullPullAt(userA, fullPullAt)
        val newerDecks = cursor("2026-10-22T11:00:00Z", "deck-10")

        store.saveCursor(userA, SyncTable.DECKS, newerDecks)

        val loaded = store.load(userA)!!
        assertEquals(newerDecks, loaded.cursors[SyncTable.DECKS])
        assertEquals(topics, loaded.cursors[SyncTable.TOPICS], "el cursor de topics no se pisa")
        assertEquals(fullPullAt, loaded.lastFullPullAt, "la fecha del pull completo no se pisa")
    }

    @Test
    fun `guardar la fecha del pull completo conserva los cursores`() = runBlocking {
        val store = DataStorePullWatermarkStore(FakeSettingsDataStore())
        store.saveCursor(userA, SyncTable.FLASHCARDS, flashcards)

        store.saveFullPullAt(userA, fullPullAt)

        assertEquals(flashcards, store.load(userA)!!.cursors[SyncTable.FLASHCARDS])
    }

    @Test
    fun `lo guardado por otro usuario se ignora`() = runBlocking {
        val store = DataStorePullWatermarkStore(FakeSettingsDataStore())
        store.saveCursor(userA, SyncTable.DECKS, decks)

        assertNull(store.load(userB))
        assertEquals(decks, store.load(userA)!!.cursors[SyncTable.DECKS])
    }

    @Test
    fun `al guardar como otro usuario se empieza de cero y no se reutiliza nada del anterior`() = runBlocking {
        val store = DataStorePullWatermarkStore(FakeSettingsDataStore())
        store.saveCursor(userA, SyncTable.DECKS, decks)
        store.saveCursor(userA, SyncTable.TOPICS, topics)
        store.saveFullPullAt(userA, fullPullAt)

        store.saveCursor(userB, SyncTable.FLASHCARDS, flashcards)

        val b = store.load(userB)!!
        assertEquals(mapOf(SyncTable.FLASHCARDS to flashcards), b.cursors, "solo lo de B")
        assertNull(b.lastFullPullAt, "la fecha de pull completo de A no se hereda")
        assertNull(store.load(userA), "lo de A ya no esta")
    }

    @Test
    fun `un JSON corrupto se trata como sin marca sin lanzar excepcion`() = runBlocking {
        val store = DataStorePullWatermarkStore(FakeSettingsDataStore(preferencesOf(watermarkKey to "{esto no es json")))

        assertNull(store.load(userA))
    }

    @Test
    fun `tras un JSON corrupto se puede volver a guardar y leer`() = runBlocking {
        val store = DataStorePullWatermarkStore(FakeSettingsDataStore(preferencesOf(watermarkKey to "\u0000basura")))

        store.saveCursor(userA, SyncTable.DECKS, decks)

        assertEquals(decks, store.load(userA)!!.cursors[SyncTable.DECKS])
    }

    @Test
    fun `una version de formato desconocida se trata como sin marca`() = runBlocking {
        val json = """{"v":2,"userId":"$userA","cursors":{"decks":{"ts":"2026-10-22T10:00:00Z","id":"d"}}}"""
        val store = DataStorePullWatermarkStore(FakeSettingsDataStore(preferencesOf(watermarkKey to json)))

        assertNull(store.load(userA))
    }

    @Test
    fun `un cursor con instante ilegible solo deja esa tabla sin marca y conserva las demas`() = runBlocking {
        val json = """{"v":1,"userId":"$userA","fullPullAt":"no-es-fecha","cursors":{
            "decks":{"ts":"no-es-fecha","id":"d"},
            "topics":{"ts":"2026-10-22T10:00:01Z","id":"topic-9"}}}"""
        val store = DataStorePullWatermarkStore(FakeSettingsDataStore(preferencesOf(watermarkKey to json)))

        val loaded = store.load(userA)!!

        assertNull(loaded.cursors[SyncTable.DECKS])
        assertEquals(topics, loaded.cursors[SyncTable.TOPICS])
        assertNull(loaded.lastFullPullAt)
    }

    @Test
    fun `una tabla desconocida en el JSON se ignora`() = runBlocking {
        val json = """{"v":1,"userId":"$userA","cursors":{"otra_tabla":{"ts":"2026-10-22T10:00:00Z","id":"x"}}}"""
        val store = DataStorePullWatermarkStore(FakeSettingsDataStore(preferencesOf(watermarkKey to json)))

        assertEquals(emptyMap(), store.load(userA)!!.cursors)
    }

    private class BrokenDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IllegalStateException("disco no disponible") }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw IllegalStateException("disco no disponible")
    }

    @Test
    fun `si el DataStore falla al leer no hay marca y no se lanza excepcion`() = runBlocking {
        assertNull(DataStorePullWatermarkStore(BrokenDataStore()).load(userA))
    }

    @Test
    fun `si el DataStore falla al escribir no se lanza excepcion`() = runBlocking {
        val store = DataStorePullWatermarkStore(BrokenDataStore())

        store.saveCursor(userA, SyncTable.DECKS, decks)
        store.saveFullPullAt(userA, fullPullAt)

        assertNull(store.load(userA))
    }

    @Test
    fun `la implementacion sin efecto nunca devuelve marca y no guarda nada`() = runBlocking {
        NoOpPullWatermarkStore.saveCursor(userA, SyncTable.DECKS, decks)
        NoOpPullWatermarkStore.saveFullPullAt(userA, fullPullAt)

        assertNull(NoOpPullWatermarkStore.load(userA))
    }
}
