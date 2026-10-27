package org.braincards.app.data.sync

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

/**
 * Editar un PADRE en A y hacer sync incremental en B (que tiene hijos locales que el servidor NO vuelve a enviar):
 * los hijos y la planificacion de B tienen que sobrevivir. Dos Room reales + servidor PostgREST falso.
 */
class TwoDeviceParentEditRoomTest {

    private val devices = mutableListOf<Device>()
    private val base: Instant = Instant.fromEpochSeconds(kotlinx.datetime.Clock.System.now().epochSeconds)

    private fun device(name: String, server: FakeServer) =
        Device(name, server, "user-a", MutableClock(base), 2).also { devices += it }

    @AfterTest
    fun tearDown() {
        devices.forEach { it.close() }
        devices.clear()
    }

    /**
     * A crea deck d1 -> topics t1,t2 -> cards c1 (t1), c2 (t2) + planificaciones, con marcas de servidor separadas 5 min
     * (mas que el solape de 2 min): los hijos "viejos" (t1, c1) NO se vuelven a bajar en el incremental de B.
     */
    private suspend fun seeded(): Pair<Device, Device> {
        val server = FakeServer(Instant.parse("2025-03-01T07:13:41.250Z"), autoTick = 5.minutes)
        val a = device("A", server)
        val b = device("B", server)
        a.addDeck("d1")
        a.addTopic("t1", "d1")
        a.addTopic("t2", "d1")
        a.addCard("c1", "d1", topicId = "t1")
        a.addCard("c2", "d1", topicId = "t2")
        a.addScheduling("c1")
        a.addScheduling("c2")
        // Un segundo mazo, mas nuevo, con sus propios hijos: mantiene las tablas de B no vacias aunque un REPLACE en
        // cascada vaciara las de d1 (el guard de "tabla local vacia" haria entonces un pull completo y lo taparia).
        a.addDeck("d2")
        a.addTopic("t3", "d2")
        a.addCard("c3", "d2", topicId = "t3")
        a.addScheduling("c3")
        a.sync()
        b.sync()
        b.sync()
        assertEquals(3L, b.count("topics"))
        assertEquals(3L, b.count("flashcards"))
        assertEquals(3L, b.count("card_scheduling"))
        return a to b
    }

    private suspend fun assertBKeepsChildren(b: Device) {
        assertEquals(3L, b.count("topics"), "topics de B")
        assertEquals(3L, b.count("flashcards"), "flashcards de B")
        assertEquals(3L, b.count("card_scheduling"), "card_scheduling de B")
        assertNotNull(b.db.cardSchedulingDao().getByCardId("c1"))
    }

    @Test
    fun `un mazo renombrado en A llega a B y B conserva temas tarjetas y planificacion`() = runBlocking<Unit> {
        val (a, b) = seeded()

        a.clock += 10.minutes
        a.editDeck("d1", "renombrado")
        a.sync()
        val before = b.getsTo("decks").size
        b.sync()
        val filter = b.getsTo("decks")[before].url.parameters["server_updated_at"]

        assertNotNull(filter, "el sync de B fue incremental")
        assertEquals("renombrado", b.deckName("d1"))
        assertBKeepsChildren(b)
    }

    @Test
    fun `una tarjeta editada en A llega a B y B conserva su planificacion`() = runBlocking<Unit> {
        val (a, b) = seeded()

        a.clock += 10.minutes
        val c = a.flashcards.getFlashcardById("c1")!!
        a.flashcards.updateFlashcard(c.copy(question = "Q editada", updatedAt = a.clock.now()))
        a.sync()
        b.sync()

        assertEquals("Q editada", b.flashcards.getFlashcardById("c1")!!.question)
        assertBKeepsChildren(b)
    }

    @Test
    fun `un tema editado en A llega a B y B conserva sus tarjetas`() = runBlocking<Unit> {
        val (a, b) = seeded()

        a.clock += 10.minutes
        val t = a.topics.getTopicById("t1")!!
        a.topics.updateTopic(t.copy(name = "tema editado", updatedAt = a.clock.now()))
        a.sync()
        b.sync()

        assertEquals("tema editado", b.topics.getTopicById("t1")!!.name)
        assertBKeepsChildren(b)
    }

    @Test
    fun `un borrado logico del mazo en A llega a B sin borrar fisicamente sus hijos`() = runBlocking<Unit> {
        val (a, b) = seeded()

        a.clock += 10.minutes
        a.tombstoneDeck("d1", at = a.clock.now())
        a.sync()
        b.sync()

        assertEquals(true, b.deckIsTombstone("d1"))
        assertBKeepsChildren(b)
    }
}
