package org.braincards.app.data.sync

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.SyncTable
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeResult
import org.braincards.app.domain.usecase.Sync.FULL_PULL_INTERVAL
import org.braincards.app.domain.usecase.Sync.downloadedRowCount
import org.braincards.app.domain.usecase.Sync.isFullySuccessful
import org.braincards.app.presentation.sync.FinishSignOutResult
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Pull incremental de PUNTA A PUNTA con DOS dispositivos: cada uno con su Room real, su motor
 * (SyncEngineUseCase + DataStorePullWatermarkStore + LocalDataRepositoryImpl como inspector) y su reloj;
 * un servidor PostgREST falso compartido que sella server_updated_at con su propio reloj; y el
 * RemoteDataSourceImpl REAL entre medias, con paginas de 2 filas para cruzar paginas y empates.
 */
class TwoDeviceSyncRoomTest {

    private val userA = "user-a"
    private val devices = mutableListOf<Device>()

    // Hora "actual" de los dispositivos = la real: el push marca lastSyncedAt con el reloj del sistema, asi que las
    // filas creadas a esta hora quedan sincronizadas y las ediciones posteriores (+minutos) vuelven a estar pendientes.
    private val base: Instant = Instant.fromEpochSeconds(kotlinx.datetime.Clock.System.now().epochSeconds)

    // Reloj del servidor distinto del de los dispositivos (a proposito: no coinciden).
    private val serverStart: Instant = Instant.parse("2025-03-01T07:13:41.250Z")

    private fun server() = FakeServer(serverStart)

    private fun device(name: String, server: FakeServer, clockAt: Instant = base, user: String = userA, pageSize: Int = 2) =
        Device(name, server, user, MutableClock(clockAt), pageSize).also { devices += it }

    @AfterTest
    fun tearDown() {
        devices.forEach { it.close() }
        devices.clear()
    }

    /** Sincroniza y devuelve el filtro de marca de la PRIMERA pagina de [table] (null = pull completo). */
    private suspend fun Device.syncFilter(table: String = "decks", force: Boolean = false): String? {
        val before = getsTo(table).size
        sync(force)
        return getsTo(table)[before].url.parameters["server_updated_at"]
    }

    private fun deckAt(id: String, user: String, at: Instant, name: String = "Deck $id") =
        Deck(id, name, at, at, null, null, user, null)

    /** A crea [ids] y sincroniza dos veces: la segunda baja lo propio y deja cursores guardados. */
    private suspend fun Device.seedWithCursor(vararg ids: String) {
        ids.forEach { addDeck(it) }
        sync()
        sync()
    }

    // --- relojes de dispositivo desfasados -------------------------------------------------------------------------------

    @Test
    fun `un dispositivo con el reloj atrasado sube una fila con updated_at anterior y el otro la baja`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        val b = device("B", server, clockAt = base - 3.hours)
        a.seedWithCursor("d1")

        b.addDeck("d2") // updated_at = base - 3h: MAS VIEJO que todo lo que A ha visto
        b.sync()
        val filter = a.syncFilter()

        assertNotNull(filter, "A ya tiene marca: el pull es incremental")
        assertEquals("Deck d2", a.deckName("d2"))
    }

    @Test
    fun `un dispositivo con el reloj adelantado, la marca es la del servidor, no la de su updated_at`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        val b = device("B", server, clockAt = base + 5.hours)
        a.seedWithCursor("d1")

        b.addDeck("d3") // updated_at = base + 5h
        b.sync()
        a.sync()

        assertEquals("Deck d3", a.deckName("d3"))
        val cursor = a.store.load(userA)!!.cursors.getValue(SyncTable.DECKS)
        assertEquals(server.stampOf("decks", "d3"), cursor.serverUpdatedAt, "el cursor guardado es el sello del servidor")
        assertTrue(cursor.serverUpdatedAt < base, "...que no tiene nada que ver con el reloj adelantado del dispositivo")
    }

    @Test
    fun `B sube tarde una fila muy antigua (mas vieja que la marca de A) y A la baja igual`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        val b = device("B", server)
        a.seedWithCursor("d1", "d2")

        b.addDeck("viejo", at = base - 400.days) // creada hace mas de un ano, offline hasta ahora
        b.sync()
        a.sync()

        assertEquals("Deck viejo", a.deckName("viejo"))
    }

    @Test
    fun `si el reloj del dispositivo retrocede el pull es completo`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        a.seedWithCursor("d1", "d2")

        a.clock.now = base - 1.hours
        val filter = a.syncFilter()

        assertNull(filter, "la fecha del ultimo completo esta en el futuro del reloj actual")
    }

    @Test
    fun `si el reloj salta mas de 7 dias hacia delante el pull es completo`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        a.seedWithCursor("d1", "d2")

        a.clock.now = base + 30.days
        val filter = a.syncFilter()

        assertNull(filter)
    }

    // --- escritura confirmada tarde -----------------------------------------------------------------------------------------

    @Test
    fun `una escritura confirmada tarde con marca anterior al cursor pero dentro del solape se baja`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        val b = device("B", server)
        a.seedWithCursor("d1")
        val cursorTs = server.stampOf("decks", "d1")

        // Una transaccion que empezo antes confirma ahora: el trigger le puso una marca 30 s anterior al cursor de A.
        server.clock.now = cursorTs - 30.seconds
        b.addDeck("tarde")
        b.sync()
        assertTrue(server.stampOf("decks", "tarde") < cursorTs)
        a.sync()

        assertEquals("Deck tarde", a.deckName("tarde"))
    }

    @Test
    fun `una escritura mas de 2 minutos anterior al cursor no entra en el incremental pero si en el completo`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        a.seedWithCursor("d1")
        val cursorTs = server.stampOf("decks", "d1")

        server.clock.now = cursorTs - 5.minutes
        server.putDeck(deckAt("muy-tarde", userA, base))
        a.sync()
        assertNull(a.deckName("muy-tarde"), "fuera del margen: limite conocido del diseno")

        val filter = a.syncFilter(force = true)

        assertNull(filter)
        assertEquals("Deck muy-tarde", a.deckName("muy-tarde"), "el pull completo la recupera")
    }

    // --- cambio de cuenta, cierre de sesion ------------------------------------------------------------------------------

    @Test
    fun `cambio de cuenta A a B hace pull completo aunque haya marca de A`() = runBlocking<Unit> {
        val server = server()
        val d = device("D", server)
        server.putDeck(deckAt("a1", userA, base))
        server.putDeck(deckAt("a2", userA, base))
        server.putDeck(deckAt("a3", userA, base))
        // Las de B tienen sellos MUY anteriores al cursor que D guarda para A.
        server.clock.now = serverStart - 2.hours
        server.putDeck(deckAt("b1", "user-b", base))
        server.putDeck(deckAt("b2", "user-b", base))
        d.sync()
        d.sync()
        assertNotNull(d.store.load(userA))

        d.signInAs("user-b")
        val filter = d.syncFilter()

        assertNull(filter, "la marca era de otra cuenta: pull completo")
        assertEquals("Deck b1", d.deckName("b1"))
        assertEquals("Deck b2", d.deckName("b2"))
        assertNull(d.store.load(userA), "la marca guardada ya es la de B")
        assertNotNull(d.store.load("user-b"))
    }

    @Test
    fun `cerrar sesion borra la marca y un nuevo login hace pull completo`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        a.seedWithCursor("d1", "d2", "d3")
        assertNotNull(a.store.load(userA))

        val finished = a.coordinator.finishSignOut(userA, confirmedCount = 0)

        val outcome = (finished as FinishSignOutResult.Finished).outcome
        assertEquals(SignOutAndWipeResult.SignedOut, outcome)
        assertNull(a.store.load(userA), "la marca de agua se borra al cerrar sesion")
        assertEquals(0L, a.count("decks"))

        val before = a.getsTo("decks").size
        a.signInAs(userA)
        a.coordinator.syncNow() // el coordinador ya sincroniza solo al ver la sesion; esto espera a que termine

        val filter = a.getsTo("decks")[before].url.parameters["server_updated_at"]
        assertNull(filter, "sin marca: el primer pull tras el login es completo")
        assertEquals(3L, a.count("decks"))
    }

    // --- descartes: la fila reaparece cuando llega su padre -----------------------------------------------------------

    @Test
    fun `una flashcard descartada por falta de su deck reaparece horas despues cuando llega el deck`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        server.putDeck(deckAt("d0", userA, base))
        val t = base
        fun card(id: String, deck: String) =
            org.braincards.app.domain.models.Flashcard(id, deck, "Q", "A", t, t, org.braincards.app.domain.models.CardType.FLIP, null, null, null, userA, null, null)
        server.putFlashcard(card("f0", "d0"))
        server.putFlashcard(card("f1", "dX")) // su deck aun no existe en el servidor

        val first = a.sync()
        assertEquals(1, first.pull.flashcards.discarded)
        assertTrue(first.isFullySuccessful())
        assertFalse(a.hasCard("f1"))
        assertNotNull(a.store.load(userA)!!.lastFullPullAt, "los descartes no bloquean la fecha del completo")

        // Horas despues llega el deck (otro dispositivo) y A vuelve a sincronizar.
        server.clock.now += 3.hours
        server.putDeck(deckAt("dX", userA, base))
        a.clock += 3.hours
        val second = a.sync()

        assertTrue(a.hasCard("f1"), "la fila descartada vuelve a bajarse y esta vez se aplica")
        assertEquals(0, second.pull.flashcards.discarded)
        assertNotNull(a.store.load(userA)!!.cursors[SyncTable.FLASHCARDS])
    }

    // --- edicion concurrente: convergencia por LWW ---------------------------------------------------------------------------

    private suspend fun converge(newestIsB: Boolean, aSyncsFirst: Boolean) {
        val server = server()
        val a = device("A", server)
        val b = device("B", server)
        a.addDeck("d1", name = "orig")
        a.sync()
        b.sync()
        a.sync()
        b.sync()
        a.editDeck("d1", "edit-A", at = a.clock.now() + (if (newestIsB) 10 else 20).minutes)
        b.editDeck("d1", "edit-B", at = b.clock.now() + (if (newestIsB) 20 else 10).minutes)

        val order = if (aSyncsFirst) listOf(a, b) else listOf(b, a)
        repeat(2) { order.forEach { it.sync() } }

        val expected = if (newestIsB) "edit-B" else "edit-A"
        val label = "newestIsB=$newestIsB aSyncsFirst=$aSyncsFirst"
        assertEquals(expected, a.deckName("d1"), "A $label")
        assertEquals(expected, b.deckName("d1"), "B $label")
        assertEquals(expected, server.nameOf("decks", "d1"), "servidor $label")
    }

    @Test
    fun `edicion concurrente en los dos dispositivos converge en la mas reciente`() = runBlocking<Unit> {
        for (newestIsB in listOf(true, false)) {
            for (aFirst in listOf(true, false)) {
                converge(newestIsB, aFirst)
                tearDown()
            }
        }
    }

    // --- tombstones ----------------------------------------------------------------------------------------------------------

    @Test
    fun `un borrado logico en A llega a B`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        val b = device("B", server)
        a.addDeck("d1")
        a.sync()
        b.seedWithCursor()
        assertEquals(false, b.deckIsTombstone("d1"))

        a.tombstoneDeck("d1", at = a.clock.now() + 1.minutes)
        a.sync()
        b.sync()

        assertEquals(true, b.deckIsTombstone("d1"))
    }

    @Test
    fun `una tabla local con solo tombstones NO fuerza pull completo`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        a.seedWithCursor("d1")
        a.tombstoneDeck("d1", at = a.clock.now() + 1.minutes)
        assertEquals(true, a.deckIsTombstone("d1"))
        assertTrue(a.localData.isEmpty(SyncTable.DECKS).not(), "el inspector real cuenta los tombstones")

        val filter = a.syncFilter()

        assertNotNull(filter, "tabla con solo tombstones: sigue siendo incremental")
    }

    @Test
    fun `borrar la BD local con la marca guardada fuerza pull completo y repuebla`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        a.seedWithCursor("d1", "d2", "d3", "d4", "d5")
        assertNotNull(a.store.load(userA))

        a.localData.wipeAll() // la BD desaparece pero la marca (DataStore) sigue ahi
        val filter = a.syncFilter()

        assertNull(filter, "tabla local vacia con cursor: esa tabla va completa")
        assertEquals(5L, a.count("decks"))
    }

    // --- completo semanal y forzado -------------------------------------------------------------------------------------

    @Test
    fun `el pull completo semanal recupera lo que el incremental no ve y renueva la fecha`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        a.seedWithCursor("d1")
        val cursorTs = server.stampOf("decks", "d1")
        server.clock.now = cursorTs - 1.hours
        server.putDeck(deckAt("perdida", userA, base)) // invisible para el incremental

        a.clock.now = base + FULL_PULL_INTERVAL - 1.seconds
        assertNotNull(a.syncFilter(), "justo antes de 7 dias: incremental")
        assertNull(a.deckName("perdida"))

        a.clock.now = base + FULL_PULL_INTERVAL
        assertNull(a.syncFilter(), "a los 7 dias: completo")
        assertEquals("Deck perdida", a.deckName("perdida"))
        assertEquals(base + FULL_PULL_INTERVAL, a.store.load(userA)!!.lastFullPullAt)

        a.clock += 1.seconds
        assertNotNull(a.syncFilter(), "tras renovar la fecha vuelve a ser incremental")
    }

    @Test
    fun `forceFullPull ignora las marcas, recupera lo perdido y guarda las nuevas`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)
        a.seedWithCursor("d1")
        server.clock.now = server.stampOf("decks", "d1") - 1.hours
        server.putDeck(deckAt("perdida", userA, base))

        val filter = a.syncFilter(force = true)

        assertNull(filter)
        assertEquals("Deck perdida", a.deckName("perdida"))
        assertEquals(a.clock.now(), a.store.load(userA)!!.lastFullPullAt)
        assertNotNull(a.store.load(userA)!!.cursors[SyncTable.DECKS])
    }

    // --- el segundo sync sin cambios baja SOLO el solape -------------------------------------------------------------

    @Test
    fun `un segundo sync sin cambios baja solo las filas dentro del solape y nunca todas`() = runBlocking<Unit> {
        val server = server() // sellos separados 1 minuto: el solape de 2 minutos abarca 3 filas por tabla
        val a = device("A", server)
        val b = device("B", server)
        (1..6).forEach { a.addDeck("d$it") }
        (1..5).forEach { a.addTopic("t$it", "d1") }
        (1..5).forEach { a.addCard("f$it", "d1") }
        (1..5).forEach { a.addScheduling("f$it") }
        a.sync()

        val first = b.sync()
        assertEquals(21, first.pull.downloadedRowCount(), "el primero baja todo")

        val second = b.sync()

        assertEquals(3, second.pull.decks.downloaded)
        assertEquals(3, second.pull.topics.downloaded)
        assertEquals(3, second.pull.flashcards.downloaded)
        assertEquals(3, second.pull.cardScheduling.downloaded)
        assertEquals(12, second.pull.downloadedRowCount())
        assertTrue(second.pull.decks.downloaded < 6 && second.pull.topics.downloaded < 5)
        assertFalse(second.pull.decks.fullPull)
        assertEquals(6L, b.count("decks"))
    }

    @Test
    fun `un segundo sync con tablas vacias en el servidor no baja nada`() = runBlocking<Unit> {
        val server = server()
        val a = device("A", server)

        a.sync()
        val second = a.sync()

        assertEquals(0, second.pull.downloadedRowCount())
    }

    // --- paginacion: empates y escrituras durante la descarga ----------------------------------------------------------

    @Test
    fun `filas con la MISMA marca que cruzan paginas se bajan todas, sin perder ni repetir`() = runBlocking<Unit> {
        val server = server()
        server.autoTick = 0.seconds // los 5 decks comparten sello
        (1..5).forEach { server.putDeck(deckAt("d$it", userA, base)) }
        val b = device("B", server)

        val first = b.sync()

        assertEquals(5L, b.count("decks"))
        assertEquals(5, first.pull.decks.downloaded, "ni mas (repetidas) ni menos (perdidas)")
        assertEquals(3, b.getsTo("decks").size, "paginas de 2: 2 + 2 + 1")
    }

    @Test
    fun `una fila modificada en el servidor mientras se pagina no hace perder ninguna otra`() = runBlocking<Unit> {
        val server = server()
        (1..5).forEach { server.putDeck(deckAt("d$it", userA, base)) }
        // Tras la primera pagina de decks, otro cliente edita d1: su sello pasa a ser el mas reciente.
        server.afterGet = { table, index ->
            if (table == "decks" && index == 1) server.putDeck(deckAt("d1", userA, base + 1.hours, name = "d1-nuevo"))
        }
        val b = device("B", server)

        b.sync()

        assertEquals(5L, b.count("decks"), "con offset, d3 se habria perdido")
        (1..5).forEach { assertNotNull(b.deckName("d$it"), "d$it") }
        assertEquals("d1-nuevo", b.deckName("d1"))
    }
}
