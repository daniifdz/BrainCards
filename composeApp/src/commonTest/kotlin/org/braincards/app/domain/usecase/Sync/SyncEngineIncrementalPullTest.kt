package org.braincards.app.domain.usecase.Sync

import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.MissingServerUpdatedAtException
import org.braincards.app.domain.models.PullApplyReport
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.PullWatermarks
import org.braincards.app.domain.models.RemoteRow
import org.braincards.app.domain.models.SyncTable
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.LocalTableInspector
import org.braincards.app.domain.repository.PullWatermarkStore
import org.braincards.app.domain.repository.RemoteDataSource
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Motor de sync con pull INCREMENTAL (paso 4b, aun desconectado de la app): marca de agua, pull completo
 * semanal, descartes, tabla local vacia, fallback sin server_updated_at y almacen roto. Todo con fakes y
 * un "servidor" que sella cada fila con su cursor.
 */
class SyncEngineIncrementalPullTest {

    private val user = "user-1"

    // --- servidor falso -------------------------------------------------------------------------------------------

    /** Servidor que sella las filas: devuelve lo precargado, en orden, con su cursor, y anota cada llamada. */
    private class StampedRemote(private val delegate: FakeRemoteDataSource) : RemoteDataSource by delegate {
        var decks: List<RemoteRow<Deck>> = emptyList()
        var topics: List<RemoteRow<Topic>> = emptyList()
        var flashcards: List<RemoteRow<Flashcard>> = emptyList()
        var scheduling: List<RemoteRow<CardScheduling>> = emptyList()

        val sinceCalls = mutableListOf<Pair<SyncTable, PullCursor?>>()
        val fetchFailures = mutableMapOf<SyncTable, Throwable>()
        val missingColumn = mutableSetOf<SyncTable>()

        fun watermarkOf(table: SyncTable): PullCursor? = sinceCalls.last { it.first == table }.second

        private fun <T> since(table: SyncTable, rows: List<RemoteRow<T>>, w: PullCursor?): List<RemoteRow<T>> {
            sinceCalls.add(table to w)
            fetchFailures[table]?.let { throw it }
            if (table in missingColumn) throw MissingServerUpdatedAtException(table.storageKey)
            // Primera pagina con solape de 2 minutos, como el servidor real.
            return if (w == null) rows else rows.filter { it.cursor!!.serverUpdatedAt >= w.serverUpdatedAt - 2.minutes }
        }

        override suspend fun pullDecksSince(userId: String, watermark: PullCursor?) = since(SyncTable.DECKS, decks, watermark)
        override suspend fun pullTopicsSince(userId: String, watermark: PullCursor?) = since(SyncTable.TOPICS, topics, watermark)
        override suspend fun pullFlashcardsSince(userId: String, watermark: PullCursor?) = since(SyncTable.FLASHCARDS, flashcards, watermark)
        override suspend fun pullCardSchedulingSince(userId: String, watermark: PullCursor?) = since(SyncTable.CARD_SCHEDULING, scheduling, watermark)
    }

    // --- almacen, inspector y reloj falsos ---------------------------------------------------------------------

    private class RecordingStore : PullWatermarkStore {
        var owner: String? = null
        var data = PullWatermarks()
        var loadFailure: Throwable? = null
        var saveFailure: Throwable? = null
        val cursorSaves = mutableListOf<Pair<SyncTable, PullCursor>>()
        val fullPullSaves = mutableListOf<Instant>()

        fun seed(userId: String, cursors: Map<SyncTable, PullCursor> = emptyMap(), lastFullPullAt: Instant? = null) {
            owner = userId
            data = PullWatermarks(cursors, lastFullPullAt)
        }

        val savedAnything get() = cursorSaves.isNotEmpty() || fullPullSaves.isNotEmpty()

        override suspend fun load(userId: String): PullWatermarks? {
            loadFailure?.let { throw it }
            return if (owner == userId) data else null
        }

        override suspend fun saveCursor(userId: String, table: SyncTable, cursor: PullCursor) {
            saveFailure?.let { throw it }
            if (owner != userId) { owner = userId; data = PullWatermarks() }
            data = data.copy(cursors = data.cursors + (table to cursor))
            cursorSaves.add(table to cursor)
        }

        override suspend fun saveFullPullAt(userId: String, at: Instant) {
            saveFailure?.let { throw it }
            if (owner != userId) { owner = userId; data = PullWatermarks() }
            data = data.copy(lastFullPullAt = at)
            fullPullSaves.add(at)
        }
    }

    private class ToggleInspector : LocalTableInspector {
        val empty = mutableSetOf<SyncTable>()
        override suspend fun isEmpty(table: SyncTable) = table in empty
    }

    private class MutableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    // --- repositorios decorados (descartes y fallo local controlables) ----------------------------------------

    private class ApplyFailingDecks(private val delegate: DecksRepository) : DecksRepository by delegate {
        var failApply = false
        override suspend fun applyRemotePull(decks: List<Deck>): Int {
            if (failApply) throw IllegalStateException("fallo local simulado")
            return delegate.applyRemotePull(decks)
        }
    }

    private class DiscardingFlashcards(private val delegate: FlashcardRepository) : FlashcardRepository by delegate {
        var discardIds: Set<String> = emptySet()
        override suspend fun applyRemotePullReporting(flashcards: List<Flashcard>): PullApplyReport {
            val (bad, good) = flashcards.partition { it.id in discardIds }
            delegate.applyRemotePull(good)
            return PullApplyReport(applied = good.size, discarded = bad.size, discardedIds = bad.map { it.id })
        }
    }

    private class DiscardingScheduling(private val delegate: CardSchedulingRepository) : CardSchedulingRepository by delegate {
        var discardIds: Set<String> = emptySet()
        override suspend fun applyRemotePullReporting(schedulings: List<CardScheduling>): PullApplyReport {
            val (bad, good) = schedulings.partition { it.cardId in discardIds }
            delegate.applyRemotePull(good)
            return PullApplyReport(applied = good.size, discarded = bad.size, discardedIds = bad.map { it.cardId })
        }
    }

    private inner class Env(authUser: String = user) {
        val auth = FakeAuthRepository(AuthState.Authenticated(authUser, "a@b.c", sessionConfirmed = true))
        val fakeRemote = FakeRemoteDataSource()
        val remote = StampedRemote(fakeRemote)
        val store = RecordingStore()
        val inspector = ToggleInspector()
        val clock = MutableClock(NOW)
        val decks = ApplyFailingDecks(FakeDecksRepository())
        val topics = FakeTopicsRepository()
        val flashcards = DiscardingFlashcards(FakeFlashcardRepository())
        val scheduling = DiscardingScheduling(FakeCardSchedulingRepository())
        val engine = SyncEngineUseCase(
            authRepository = auth,
            remoteDataSource = remote,
            decksRepository = decks,
            topicsRepository = topics,
            flashcardRepository = flashcards,
            cardSchedulingRepository = scheduling,
            backfillLocalDataUseCase = BackfillLocalDataUseCase(decks, topics, flashcards, scheduling),
            watermarkStore = store,
            localTables = inspector,
            clock = clock
        )

        suspend fun sync(force: Boolean = false): SyncResult.Completed = engine(force) as SyncResult.Completed

        fun savedCursor(table: SyncTable): PullCursor? = store.cursorSaves.lastOrNull { it.first == table }?.second
        fun savedCursors(table: SyncTable): List<PullCursor> = store.cursorSaves.filter { it.first == table }.map { it.second }

        /** Marca previa: un cursor por tabla y un ultimo completo reciente (hace 1 dia). */
        fun seedRecent(vararg cursors: Pair<SyncTable, PullCursor>, lastFull: Instant? = NOW - 1.days) =
            store.seed(user, cursors.toMap(), lastFull)
    }

    // --- datos ------------------------------------------------------------------------------------------------------

    private fun cur(minutes: Long, id: String, seconds: Long = 0) =
        PullCursor(BASE + minutes.minutes + seconds.seconds, id)

    private val t = Instant.fromEpochMilliseconds(1000)

    private fun deckRow(id: String, c: PullCursor) =
        RemoteRow(Deck(id, "Deck $id", t, t, null, null, user, null), c)

    private fun topicRow(id: String, c: PullCursor) =
        RemoteRow(Topic(id, "d1", "Topic $id", t, t, null, null, user), c)

    private fun cardRow(id: String, c: PullCursor?) =
        RemoteRow(Flashcard(id, "d1", "Q", "A", t, t, CardType.FLIP, null, null, null, user, null, null), c)

    private fun schedRow(cardId: String, c: PullCursor?) =
        RemoteRow(CardScheduling(cardId, 2.5, 1, 1, t, t, user, null), c)

    private fun networkError() = HttpRequestException("sin conexion simulada", HttpRequestBuilder())

    // --- 1. sin marca: pull completo y se guardan los cursores ------------------------------------------------

    @Test
    fun `sin marca baja todo, guarda el cursor de la ultima fila por tabla y la fecha del completo`() = runBlocking {
        val e = Env()
        e.remote.decks = listOf(deckRow("d1", cur(10, "d1")), deckRow("d2", cur(20, "d2")))
        e.remote.flashcards = listOf(cardRow("f1", cur(30, "f1")))

        val r = e.sync()

        assertEquals(listOf<PullCursor?>(null, null, null, null), e.remote.sinceCalls.map { it.second })
        assertEquals(cur(20, "d2"), e.savedCursor(SyncTable.DECKS))
        assertEquals(cur(30, "f1"), e.savedCursor(SyncTable.FLASHCARDS))
        assertNull(e.savedCursor(SyncTable.TOPICS), "tabla vacia en el servidor: sin cursor")
        assertNull(e.savedCursor(SyncTable.CARD_SCHEDULING))
        assertEquals(listOf(NOW), e.store.fullPullSaves)
        assertTrue(r.pull.decks.fullPull && r.pull.topics.fullPull && r.pull.flashcards.fullPull && r.pull.cardScheduling.fullPull)
    }

    // --- 2. no avanza si falla -----------------------------------------------------------------------------------

    @Test
    fun `no avanza la marca si falla el fetch de la tabla`() = runBlocking {
        val e = Env()
        val old = cur(5, "d0")
        e.seedRecent(SyncTable.DECKS to old)
        e.remote.decks = listOf(deckRow("d1", cur(40, "d1")))
        e.remote.fetchFailures[SyncTable.DECKS] = networkError()

        val r = e.sync()

        assertTrue(r.pull.decks.aborted)
        assertEquals(PullFailure.NETWORK, r.pull.decks.failure)
        assertTrue(e.store.cursorSaves.none { it.first == SyncTable.DECKS })
        assertEquals(old, e.store.data.cursors[SyncTable.DECKS])
    }

    @Test
    fun `no avanza la marca si falla el apply local de la tabla`() = runBlocking {
        val e = Env()
        val old = cur(5, "d0")
        e.seedRecent(SyncTable.DECKS to old)
        e.remote.decks = listOf(deckRow("d1", cur(40, "d1")))
        e.decks.failApply = true

        val r = e.sync()

        assertTrue(r.pull.decks.aborted)
        assertEquals(PullFailure.LOCAL, r.pull.decks.failure)
        assertTrue(e.store.cursorSaves.none { it.first == SyncTable.DECKS })
        assertEquals(old, e.store.data.cursors[SyncTable.DECKS])
    }

    // --- 3. avanza con exito, y el motor pasa el cursor tal cual -----------------------------------------------

    @Test
    fun `con marca pasa el cursor guardado tal cual a Since y avanza a la ultima fila`() = runBlocking {
        val e = Env()
        val old = cur(10, "d1", seconds = 7)
        e.seedRecent(SyncTable.DECKS to old)
        e.remote.decks = listOf(deckRow("d0", cur(1, "d0")), deckRow("d1", old), deckRow("d2", cur(50, "d2")), deckRow("d3", cur(60, "d3")))

        val r = e.sync()

        assertEquals(old, e.remote.watermarkOf(SyncTable.DECKS))
        assertEquals(cur(60, "d3"), e.savedCursor(SyncTable.DECKS))
        assertEquals(3, r.pull.decks.downloaded, "d0 queda fuera del solape; d1 (el propio cursor), d2 y d3 entran")
        assertFalse(r.pull.decks.fullPull)
        assertTrue(e.store.fullPullSaves.isEmpty(), "un pull incremental no refresca la fecha del completo")
    }

    @Test
    fun `sin filas nuevas el cursor no cambia y no se escribe nada`() = runBlocking {
        val e = Env()
        e.seedRecent(SyncTable.DECKS to cur(10, "d1"))
        e.remote.decks = listOf(deckRow("d1", cur(10, "d1")))
        e.remote.decks = emptyList()

        e.sync()

        assertTrue(e.store.cursorSaves.none { it.first == SyncTable.DECKS })
    }

    @Test
    fun `un segundo sync sin cambios en el servidor no baja filas`() = runBlocking {
        val e = Env()
        e.remote.decks = listOf(deckRow("d1", cur(10, "d1")), deckRow("d2", cur(20, "d2")))
        e.remote.flashcards = listOf(cardRow("f1", cur(30, "f1")))

        val first = e.sync()
        val second = e.sync()

        assertEquals(3, first.pull.downloadedRowCount())
        // Con el solape de 2 minutos solo reaparece lo que cae dentro de la ventana de la ultima fila;
        // aqui las filas estan a 10 minutos de distancia, asi que cada tabla solo repite su ultima.
        assertEquals(1, second.pull.decks.downloaded)
        assertEquals(1, second.pull.flashcards.downloaded)
        assertEquals(0, second.pull.topics.downloaded)
        assertFalse(second.pull.decks.fullPull)
        assertEquals(cur(20, "d2"), e.store.data.cursors[SyncTable.DECKS])
    }

    // --- 4. descartes ----------------------------------------------------------------------------------------------

    @Test
    fun `con descartes el cursor no pasa de la primera descartada y la fecha del completo se guarda`() = runBlocking {
        val e = Env()
        e.remote.flashcards = listOf(
            cardRow("f1", cur(10, "f1")), cardRow("f2", cur(20, "f2")), cardRow("f3", cur(30, "f3"))
        )
        e.flashcards.discardIds = setOf("f2", "f3")

        val r = e.sync()

        assertEquals(2, r.pull.flashcards.discarded)
        assertEquals(cur(10, "f1"), e.savedCursor(SyncTable.FLASHCARDS), "la fila anterior a la primera descartada")
        assertEquals(listOf(NOW), e.store.fullPullSaves, "los descartes NO bloquean la fecha del completo")
    }

    @Test
    fun `la primera descartada en medio de otras buenas fija el cursor en la fila anterior en orden del servidor`() = runBlocking {
        val e = Env()
        e.remote.flashcards = listOf(
            cardRow("f1", cur(10, "f1")), cardRow("f2", cur(20, "f2")), cardRow("f3", cur(30, "f3")), cardRow("f4", cur(40, "f4"))
        )
        e.flashcards.discardIds = setOf("f3")

        e.sync()

        assertEquals(cur(20, "f2"), e.savedCursor(SyncTable.FLASHCARDS))
    }

    @Test
    fun `si la primera fila es la descartada no se guarda cursor de esa tabla`() = runBlocking {
        val e = Env()
        e.remote.flashcards = listOf(cardRow("f1", cur(10, "f1")), cardRow("f2", cur(20, "f2")))
        e.flashcards.discardIds = setOf("f1")

        e.sync()

        assertNull(e.savedCursor(SyncTable.FLASHCARDS))
    }

    @Test
    fun `la fila descartada reaparece en el siguiente sync cuando llega su padre`() = runBlocking {
        val e = Env()
        e.remote.flashcards = listOf(
            cardRow("f1", cur(10, "f1")), cardRow("f2", cur(10, "f2", seconds = 30)), cardRow("f3", cur(10, "f3", seconds = 40))
        )
        e.flashcards.discardIds = setOf("f2")
        e.sync()
        assertEquals(cur(10, "f1"), e.savedCursor(SyncTable.FLASHCARDS))

        e.flashcards.discardIds = emptySet() // el padre ya esta
        val r = e.sync()

        // Reaparece: la primera pagina arranca 2 minutos antes del cursor guardado.
        assertEquals(cur(10, "f1"), e.remote.watermarkOf(SyncTable.FLASHCARDS))
        assertEquals(3, r.pull.flashcards.downloaded)
        assertEquals(3, r.pull.flashcards.applied)
        assertEquals(cur(10, "f3", seconds = 40), e.savedCursor(SyncTable.FLASHCARDS))
    }

    @Test
    fun `en card_scheduling el descarte se identifica por card_id y no pasa de la primera descartada`() = runBlocking {
        val e = Env()
        e.remote.scheduling = listOf(
            schedRow("c1", cur(10, "c1")), schedRow("c2", cur(20, "c2")), schedRow("c3", cur(30, "c3"))
        )
        e.scheduling.discardIds = setOf("c2")

        val r = e.sync()

        assertEquals(1, r.pull.cardScheduling.discarded)
        assertEquals(cur(10, "c1"), e.savedCursor(SyncTable.CARD_SCHEDULING))
    }

    // --- 5. tabla local vacia -------------------------------------------------------------------------------------

    @Test
    fun `con marca pero la tabla local vacia esa tabla va completa y las demas siguen incrementales`() = runBlocking {
        val e = Env()
        e.seedRecent(SyncTable.DECKS to cur(10, "d1"), SyncTable.FLASHCARDS to cur(20, "f1"))
        e.inspector.empty.add(SyncTable.FLASHCARDS)
        e.remote.flashcards = listOf(cardRow("f1", cur(20, "f1")), cardRow("f2", cur(30, "f2")))

        val r = e.sync()

        assertNull(e.remote.watermarkOf(SyncTable.FLASHCARDS), "tabla vacia: sin cursor")
        assertEquals(cur(10, "d1"), e.remote.watermarkOf(SyncTable.DECKS))
        assertEquals(2, r.pull.flashcards.downloaded)
        assertTrue(r.pull.flashcards.fullPull)
        assertFalse(r.pull.decks.fullPull)
    }

    @Test
    fun `si la tabla local no esta vacia el cursor se usa`() = runBlocking {
        val e = Env()
        e.seedRecent(SyncTable.FLASHCARDS to cur(20, "f1"))

        e.sync()

        assertEquals(cur(20, "f1"), e.remote.watermarkOf(SyncTable.FLASHCARDS))
    }

    // --- 6. pull completo semanal ----------------------------------------------------------------------------------

    private fun Env.seedAt(lastFull: Instant?) = seedRecent(
        SyncTable.DECKS to cur(10, "d1"), SyncTable.TOPICS to cur(10, "t1"),
        SyncTable.FLASHCARDS to cur(10, "f1"), SyncTable.CARD_SCHEDULING to cur(10, "c1"),
        lastFull = lastFull
    )

    private fun Env.allWatermarksNull() = remote.sinceCalls.all { it.second == null }
    private fun Env.noWatermarkNull() = remote.sinceCalls.all { it.second != null }

    @Test
    fun `justo antes de los 7 dias sigue siendo incremental`() = runBlocking {
        val e = Env()
        e.seedAt(NOW - FULL_PULL_INTERVAL + 1.seconds)

        e.sync()

        assertEquals(4, e.remote.sinceCalls.size)
        assertTrue(e.noWatermarkNull())
        assertTrue(e.store.fullPullSaves.isEmpty())
    }

    @Test
    fun `a los 7 dias exactos o despues baja todo y guarda la nueva fecha`() = runBlocking {
        for (age in listOf(FULL_PULL_INTERVAL, FULL_PULL_INTERVAL + 1.seconds, 30.days)) {
            val e = Env()
            e.seedAt(NOW - age)

            e.sync()

            assertTrue(e.allWatermarksNull(), "edad $age")
            assertEquals(listOf(NOW), e.store.fullPullSaves, "edad $age")
        }
    }

    @Test
    fun `una fecha de completo en el futuro obliga a pull completo`() = runBlocking {
        val e = Env()
        e.seedAt(NOW + 1.days)

        e.sync()

        assertTrue(e.allWatermarksNull())
        assertEquals(listOf(NOW), e.store.fullPullSaves)
    }

    @Test
    fun `cursores sin fecha del ultimo completo obligan a pull completo`() = runBlocking {
        val e = Env()
        e.seedAt(null)

        e.sync()

        assertTrue(e.allWatermarksNull())
        assertEquals(listOf(NOW), e.store.fullPullSaves)
    }

    // --- 7. forceFullPull --------------------------------------------------------------------------------------------

    @Test
    fun `forceFullPull ignora las marcas y guarda las nuevas`() = runBlocking {
        val e = Env()
        e.seedAt(NOW - 1.days)
        e.remote.decks = listOf(deckRow("d9", cur(90, "d9")))

        val r = e.sync(force = true)

        assertTrue(e.allWatermarksNull())
        assertEquals(cur(90, "d9"), e.savedCursor(SyncTable.DECKS))
        assertEquals(listOf(NOW), e.store.fullPullSaves)
        assertTrue(r.pull.decks.fullPull)
    }

    // --- 8. cursores nulos = comportamiento de siempre ---------------------------------------------------------------

    @Test
    fun `si el remoto no da cursores el sync es el de siempre y no se guarda nada`() = runBlocking {
        val e = Env()
        e.remote.flashcards = listOf(cardRow("f1", null), cardRow("f2", null))

        val r = e.sync()

        assertFalse(r.pull.flashcards.aborted)
        assertEquals(2, r.pull.flashcards.applied)
        assertFalse(e.store.savedAnything, "sin cursores no se guarda ni cursor ni fecha")
    }

    // --- 9. almacen roto ---------------------------------------------------------------------------------------------

    @Test
    fun `un almacen que falla al leer y al escribir no rompe el sync y baja todo`() = runBlocking {
        val e = Env()
        e.store.loadFailure = IllegalStateException("datastore roto")
        e.store.saveFailure = IllegalStateException("datastore roto")
        e.remote.decks = listOf(deckRow("d1", cur(10, "d1")))

        val r = e.sync()

        assertTrue(e.allWatermarksNull())
        assertEquals(1, r.pull.decks.applied)
        assertEquals(0, r.pull.abortedTableCount())
        assertTrue(r.isFullySuccessful())
    }

    // --- 10. la fecha del completo la bloquean los fallos -----------------------------------------------------------

    @Test
    fun `si una tabla falla no se guarda la fecha del completo pero si los cursores de las otras`() = runBlocking {
        val e = Env()
        e.remote.decks = listOf(deckRow("d1", cur(10, "d1")))
        e.remote.topics = listOf(topicRow("t1", cur(10, "t1")))
        e.remote.fetchFailures[SyncTable.TOPICS] = networkError()

        val r = e.sync()

        assertTrue(r.pull.topics.aborted)
        assertTrue(e.store.fullPullSaves.isEmpty())
        assertEquals(cur(10, "d1"), e.savedCursor(SyncTable.DECKS))
        assertNull(e.savedCursor(SyncTable.TOPICS))
    }

    @Test
    fun `si una tabla falla al aplicar tampoco se guarda la fecha del completo`() = runBlocking {
        val e = Env()
        e.remote.decks = listOf(deckRow("d1", cur(10, "d1")))
        e.decks.failApply = true

        e.sync()

        assertTrue(e.store.fullPullSaves.isEmpty())
    }

    @Test
    fun `un pull incremental parcial no guarda la fecha del completo`() = runBlocking {
        val e = Env()
        e.seedRecent(SyncTable.DECKS to cur(10, "d1"))

        e.sync()

        assertTrue(e.store.fullPullSaves.isEmpty())
    }

    // --- 11. cambio de cuenta --------------------------------------------------------------------------------------

    @Test
    fun `una marca de otra cuenta se ignora y se hace pull completo`() = runBlocking {
        val e = Env(authUser = "user-2")
        e.store.seed("user-1", mapOf(SyncTable.DECKS to cur(10, "d1")), lastFullPullAt = NOW - 1.days)

        e.sync()

        assertTrue(e.allWatermarksNull())
        assertEquals("user-2", e.store.owner)
    }

    // --- 12. fallback sin server_updated_at ---------------------------------------------------------------------------

    @Test
    fun `sin la columna en el servidor hace el pull clasico, no avanza marca ni es un fallo de red`() = runBlocking {
        val e = Env()
        val old = cur(5, "f0")
        e.seedRecent(SyncTable.FLASHCARDS to old)
        e.remote.missingColumn.add(SyncTable.FLASHCARDS)
        e.remote.decks = listOf(deckRow("d1", cur(10, "d1")))
        e.fakeRemote.flashcardsToPull = listOf(
            cardRow("f1", null).row, cardRow("f2", null).row
        )

        val r = e.sync(force = true)

        val cards = r.pull.flashcards
        assertFalse(cards.aborted)
        assertNull(cards.failure)
        assertTrue(cards.fellBackToFullPull)
        assertEquals(2, cards.applied)
        assertEquals(2, cards.downloaded)
        assertTrue(e.fakeRemote.calls.contains("pullFlashcards"), "pull clasico")
        assertTrue(e.store.cursorSaves.none { it.first == SyncTable.FLASHCARDS })
        assertEquals(old, e.store.data.cursors[SyncTable.FLASHCARDS], "la marca vieja no se toca")
        assertTrue(e.store.fullPullSaves.isEmpty(), "el fallback bloquea la fecha del completo")
        assertEquals(cur(10, "d1"), e.savedCursor(SyncTable.DECKS), "las otras tablas avanzan")
        assertEquals(0, r.pull.abortedTableCount())
        assertTrue(r.isFullySuccessful(), "el sync cuenta como correcto")
    }

    @Test
    fun `si el fallback clasico tambien falla la tabla se aborta como red`() = runBlocking {
        val e = Env()
        e.remote.missingColumn.add(SyncTable.TOPICS)
        e.fakeRemote.pullTopicsFailure = networkError()

        val r = e.sync()

        assertTrue(r.pull.topics.aborted)
        assertEquals(PullFailure.NETWORK, r.pull.topics.failure)
    }

    // --- 13. contadores ------------------------------------------------------------------------------------------------

    @Test
    fun `downloaded cuenta las filas bajadas por tabla y downloadedRowCount las suma`() = runBlocking {
        val e = Env()
        e.remote.decks = listOf(deckRow("d1", cur(10, "d1")), deckRow("d2", cur(20, "d2")))
        e.remote.topics = listOf(topicRow("t1", cur(10, "t1")))
        e.remote.flashcards = listOf(cardRow("f1", cur(10, "f1")), cardRow("f2", cur(20, "f2")), cardRow("f3", cur(30, "f3")))
        e.remote.scheduling = listOf(schedRow("f1", cur(10, "f1")))

        val r = e.sync()

        assertEquals(2, r.pull.decks.downloaded)
        assertEquals(1, r.pull.topics.downloaded)
        assertEquals(3, r.pull.flashcards.downloaded)
        assertEquals(1, r.pull.cardScheduling.downloaded)
        assertEquals(7, r.pull.downloadedRowCount())
    }

    @Test
    fun `downloaded cuenta tambien las descartadas pero una tabla abortada cuenta cero`() = runBlocking {
        val e = Env()
        e.remote.flashcards = listOf(cardRow("f1", cur(10, "f1")), cardRow("f2", cur(20, "f2")))
        e.flashcards.discardIds = setOf("f2")
        e.remote.fetchFailures[SyncTable.DECKS] = networkError()

        val r = e.sync()

        assertEquals(2, r.pull.flashcards.downloaded)
        assertEquals(1, r.pull.flashcards.applied)
        assertEquals(0, r.pull.decks.downloaded)
        assertEquals(2, r.pull.downloadedRowCount())
    }

    @Test
    fun `el cursor guardado lo dio el servidor aunque las filas lleguen desordenadas por id`() = runBlocking {
        val e = Env()
        // Misma marca, ids "z" antes que "a" en el orden del servidor: el cursor es el de la ULTIMA fila.
        e.remote.decks = listOf(deckRow("z", cur(10, "z")), deckRow("a", cur(10, "a", seconds = 1)))

        e.sync()

        assertNotNull(e.savedCursor(SyncTable.DECKS))
        assertEquals(cur(10, "a", seconds = 1), e.savedCursor(SyncTable.DECKS))
    }

    private companion object {
        val BASE: Instant = Instant.parse("2026-01-01T00:00:00Z")
        val NOW: Instant = BASE + 30.days
    }
}
