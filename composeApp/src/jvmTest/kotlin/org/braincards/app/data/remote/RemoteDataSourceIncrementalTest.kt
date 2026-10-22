package org.braincards.app.data.remote

import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.MissingServerUpdatedAtException
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.Topic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pull incremental (pull...Since) de RemoteDataSourceImpl REAL contra un motor Ktor falso: se
 * comprueba la peticion que construye (orden, limit, filtros de cursor, formato UTC con Z) y como
 * devuelve filas y cursores. El motor sigue usando el pull completo; esto es solo la capa remota.
 */
class RemoteDataSourceIncrementalTest {

    private val user = "user-1"

    // --- helpers ---------------------------------------------------------------------------------

    private val orHeader = Regex("""server_updated_at\.(?:gt|eq)\.([^,)]+)""")

    /** Los dos timestamps (gt y eq) que lleva el filtro `or` de una pagina siguiente. */
    private fun orTimestamps(or: String): List<String> = orHeader.findAll(or).map { it.groupValues[1] }.toList()

    private fun assertUtcZ(value: String) {
        assertTrue(value.endsWith("Z"), "debe acabar en Z: $value")
        assertFalse('+' in value, "no debe llevar offset +00:00: $value")
    }

    // --- primera pagina --------------------------------------------------------------------------

    @Test
    fun `sin marca la primera pagina no lleva filtro de cursor ni offset`() = runBlocking {
        val (remote, engine) = stubRemote { okJson("[]") }

        val rows = remote.pullDecksSince(user, watermark = null)

        assertEquals(emptyList(), rows)
        val request = engine.requests.single()
        assertEquals("GET", request.method.value)
        assertEquals("decks", request.table())
        assertEquals("eq.$user", request.param("user_id"))
        assertEquals("server_updated_at.asc.nullslast,id.asc.nullslast", request.param("order"))
        assertEquals("1000", request.param("limit"))
        assertNull(request.param("server_updated_at"), "sin marca no hay filtro de marca")
        assertNull(request.param("or"))
        assertNull(request.param("offset"), "el pull por cursor no usa offset")
        assertNull(request.param("updated_at"), "no se filtra por el reloj del cliente")
    }

    @Test
    fun `con marca la primera pagina pide gte marca menos dos minutos en UTC con Z`() = runBlocking {
        val (remote, engine) = stubRemote { okJson("[]") }
        val watermark = PullCursor(Instant.parse("2026-10-22T10:00:00.500Z"), "d9")

        remote.pullDecksSince(user, watermark)

        val request = engine.requests.single()
        assertEquals("gte.2026-10-22T09:58:00.500Z", request.param("server_updated_at"))
        assertNull(request.param("or"), "la primera pagina no usa el cursor estricto")
        assertNull(request.param("offset"))
        assertEquals(2, PULL_OVERLAP_MARGIN.inWholeMinutes.toInt())
    }

    // --- fracciones de segundo: el valor exacto y el sufijo Z se conservan ---------------------------

    /** (texto que devuelve PostgREST, instante que representa). Postgres omite los ceros finales. */
    private val fractionCases = listOf(
        "2026-10-22T10:00:00.120000+00:00" to Instant.parse("2026-10-22T10:00:00.120Z"),
        "2026-10-22T10:00:00.12+00:00" to Instant.parse("2026-10-22T10:00:00.120Z"),
        "2026-10-22T10:00:00.123456+00:00" to Instant.parse("2026-10-22T10:00:00.123456Z"),
        "2026-10-22T10:00:00+00:00" to Instant.parse("2026-10-22T10:00:00Z"),
    )

    @Test
    fun `las paginas siguientes conservan el valor exacto de la marca y el sufijo Z`() {
        for ((serverText, expected) in fractionCases) {
            val (remote, engine) = stubRemote(pageSize = 2) { request ->
                if (request.param("or") == null) {
                    okJson("[${deckRow("d1", "2026-10-22T09:00:00+00:00")},${deckRow("d2", serverText)}]")
                } else {
                    okJson("[]")
                }
            }

            val rows = runBlocking { remote.pullDecksSince(user, watermark = null) }

            assertEquals(PullCursor(expected, "d2"), rows.last().cursor, "cursor de la fila: $serverText")
            val second = engine.requests[1]
            val timestamps = orTimestamps(second.param("or")!!)
            assertEquals(2, timestamps.size, "gt y eq llevan la misma marca")
            for (ts in timestamps) {
                assertUtcZ(ts)
                assertEquals(expected, Instant.parse(ts), "mismo instante exacto para $serverText -> $ts")
            }
        }
    }

    @Test
    fun `la marca de la primera pagina conserva fracciones y sufijo Z`() {
        for ((_, instant) in fractionCases) {
            val (remote, engine) = stubRemote { okJson("[]") }

            runBlocking { remote.pullDecksSince(user, PullCursor(instant, "x")) }

            val value = engine.requests.single().param("server_updated_at")!!
            assertTrue(value.startsWith("gte."))
            val ts = value.removePrefix("gte.")
            assertUtcZ(ts)
            assertEquals(instant - PULL_OVERLAP_MARGIN, Instant.parse(ts))
        }
    }

    // --- paginas siguientes: cursor estricto de la ULTIMA fila --------------------------------------

    @Test
    fun `las paginas siguientes usan el cursor estricto de la ultima fila sin gte ni offset`() = runBlocking {
        served = 0
        val (remote, engine) = stubRemote(pageSize = 2) {
            when (served++) {
                0 -> okJson("[${deckRow("d1", "2026-10-22T10:00:00+00:00")},${deckRow("d2", "2026-10-22T10:00:05+00:00")}]")
                else -> okJson("[${deckRow("d3", "2026-10-22T10:00:09+00:00")}]")
            }
        }

        val rows = remote.pullDecksSince(user, watermark = PullCursor(Instant.parse("2026-10-22T09:00:00Z"), "a"))

        assertEquals(listOf("d1", "d2", "d3"), rows.map { it.row.id })
        assertEquals(2, engine.requests.size)
        val second = engine.requests[1]
        assertEquals(
            "(server_updated_at.gt.2026-10-22T10:00:05Z,and(server_updated_at.eq.2026-10-22T10:00:05Z,id.gt.d2))",
            second.param("or")
        )
        assertNull(second.param("server_updated_at"), "las paginas siguientes no llevan el gte del solape")
        assertNull(second.param("offset"))
        assertEquals("server_updated_at.asc.nullslast,id.asc.nullslast", second.param("order"))
    }

    private var served = 0

    @Test
    fun `una pagina exactamente llena obliga a pedir otra que viene vacia`() = runBlocking {
        served = 0
        val (remote, engine) = stubRemote(pageSize = 2) {
            when (served++) {
                0 -> okJson("[${deckRow("d1", "2026-10-22T10:00:00+00:00")},${deckRow("d2", "2026-10-22T10:00:01+00:00")}]")
                else -> okJson("[]")
            }
        }

        val rows = remote.pullDecksSince(user, null)

        assertEquals(2, rows.size)
        assertEquals(2, engine.requests.size)
    }

    @Test
    fun `filas con la misma marca repartidas entre dos paginas se desempatan por la clave`() = runBlocking {
        served = 0
        val same = "2026-10-22T10:00:00.123456+00:00"
        val (remote, engine) = stubRemote(pageSize = 2) {
            when (served++) {
                0 -> okJson("[${deckRow("a1", same)},${deckRow("a2", same)}]")
                else -> okJson("[${deckRow("a3", same)}]")
            }
        }

        val rows = remote.pullDecksSince(user, null)

        assertEquals(listOf("a1", "a2", "a3"), rows.map { it.row.id })
        val or = engine.requests[1].param("or")!!
        assertTrue("id.gt.a2" in or, "el desempate usa el id de la ULTIMA fila: $or")
        assertEquals(2, orTimestamps(or).size)
        assertEquals(1, orTimestamps(or).distinct().size, "las dos marcas del filtro son la misma")
    }

    @Test
    fun `un fallo en la segunda pagina se propaga y no se devuelven filas a medias`() {
        served = 0
        val (remote, _) = stubRemote(pageSize = 2) {
            when (served++) {
                0 -> okJson("[${deckRow("d1", "2026-10-22T10:00:00+00:00")},${deckRow("d2", "2026-10-22T10:00:01+00:00")}]")
                else -> HttpStatusCode.InternalServerError to """{"message":"boom"}"""
            }
        }

        assertFailsWith<Exception> { runBlocking { remote.pullDecksSince(user, null) } }
    }

    // --- cursores, tombstones y las cuatro tablas -------------------------------------------------------

    @Test
    fun `devuelve el mismo dominio que el pull completo y el cursor de cada fila`() = runBlocking {
        val (remote, _) = stubRemote {
            okJson("[${deckRow("d1", "2026-10-22T10:00:00.250000+00:00")}]")
        }

        val rows = remote.pullDecksSince(user, null)
        val full = remote.pullDecks(user, Instant.fromEpochMilliseconds(0))

        assertEquals(full, rows.map { it.row })
        assertEquals(PullCursor(Instant.parse("2026-10-22T10:00:00.250Z"), "d1"), rows.single().cursor)
    }

    @Test
    fun `los tombstones (deleted_at) se devuelven como cualquier otra fila`() = runBlocking {
        val (remote, _) = stubRemote {
            okJson("[${deckRow("d1", "2026-10-22T10:00:00+00:00", deletedAt = "2026-10-22T09:59:00+00:00")}]")
        }

        val row = remote.pullDecksSince(user, null).single().row

        assertEquals(Instant.parse("2026-10-22T09:59:00Z"), row.deletedAt)
    }

    @Test
    fun `las cuatro tablas piden su tabla y su clave de desempate`() = runBlocking {
        val (remote, engine) = stubRemote { request ->
            when (request.table()) {
                "decks" -> okJson("[${deckRow("d1", "2026-10-22T10:00:00+00:00")}]")
                "topics" -> okJson("[${topicRow("t1", "2026-10-22T10:00:00+00:00")}]")
                "flashcards" -> okJson("[${flashcardRow("f1", "2026-10-22T10:00:00+00:00")}]")
                else -> okJson("[${schedulingRow("c1", "2026-10-22T10:00:00+00:00")}]")
            }
        }

        val decks: List<Deck> = remote.pullDecksSince(user, null).map { it.row }
        val topics: List<Topic> = remote.pullTopicsSince(user, null).map { it.row }
        val cards: List<Flashcard> = remote.pullFlashcardsSince(user, null).map { it.row }
        val scheduling: List<CardScheduling> = remote.pullCardSchedulingSince(user, null).map { it.row }

        assertEquals(listOf("d1"), decks.map { it.id })
        assertEquals(listOf("t1"), topics.map { it.id })
        assertEquals(listOf("f1"), cards.map { it.id })
        assertEquals(CardType.FLIP, cards.single().cardType)
        assertEquals(listOf("c1"), scheduling.map { it.cardId })
        assertEquals(
            listOf("decks", "topics", "flashcards", "card_scheduling"),
            engine.requests.map { it.table() }
        )
        assertEquals("server_updated_at.asc.nullslast,id.asc.nullslast", engine.requests[0].param("order"))
        assertEquals("server_updated_at.asc.nullslast,id.asc.nullslast", engine.requests[1].param("order"))
        assertEquals("server_updated_at.asc.nullslast,id.asc.nullslast", engine.requests[2].param("order"))
        assertEquals("server_updated_at.asc.nullslast,card_id.asc.nullslast", engine.requests[3].param("order"))
    }

    @Test
    fun `card_scheduling pagina con card_id como desempate`() = runBlocking {
        served = 0
        val (remote, engine) = stubRemote(pageSize = 1) {
            when (served++) {
                0 -> okJson("[${schedulingRow("c1", "2026-10-22T10:00:00+00:00")}]")
                else -> okJson("[]")
            }
        }

        remote.pullCardSchedulingSince(user, null)

        assertEquals(
            "(server_updated_at.gt.2026-10-22T10:00:00Z,and(server_updated_at.eq.2026-10-22T10:00:00Z,card_id.gt.c1))",
            engine.requests[1].param("or")
        )
    }

    // --- server_updated_at ausente: error PROPIO, sin el message de una RestException ---------------------------

    private val undefinedColumnBody =
        """{"code":"42703","message":"column decks.server_updated_at does not exist","details":null,"hint":null}"""

    private fun assertSafeMissingColumnError(e: MissingServerUpdatedAtException, table: String) {
        assertEquals(table, e.table)
        val text = e.message.orEmpty()
        assertTrue(table in text && "sql/server_updated_at.sql" in text, "mensaje claro: $text")
        // Nada que venga del servidor ni de la peticion (cabeceras, Authorization, URL).
        for (forbidden in listOf("does not exist", "Headers", "Authorization", "URL:", "Http Method", "apikey", "anon-key")) {
            assertFalse(forbidden in text, "el mensaje no debe contener '$forbidden': $text")
            assertFalse(forbidden in e.stackTraceToString(), "ni el stack trace: '$forbidden'")
        }
        assertNull(e.cause, "sin causa: la causa arrastraria el message de la RestException")
    }

    @Test
    fun `una columna inexistente (400 42703) lanza un error propio sin el message de la RestException`() {
        val (remote, _) = stubRemote { HttpStatusCode.BadRequest to undefinedColumnBody }

        val e = assertFailsWith<MissingServerUpdatedAtException> {
            runBlocking { remote.pullDecksSince(user, null) }
        }

        assertSafeMissingColumnError(e, "decks")
    }

    @Test
    fun `el mismo 400 por el camino normal SI arrastra cabeceras (por eso el error propio no lleva la causa)`() {
        val (remote, _) = stubRemote { HttpStatusCode.BadRequest to undefinedColumnBody }

        val e = assertFailsWith<PostgrestRestException> {
            runBlocking { remote.pullDecks(user, Instant.fromEpochMilliseconds(0)) }
        }

        assertTrue("Headers" in e.message.orEmpty(), "el message de una RestException incluye las cabeceras")
    }

    @Test
    fun `un 400 con otro codigo no se confunde con la columna ausente`() {
        val (remote, _) = stubRemote {
            HttpStatusCode.BadRequest to """{"code":"PGRST100","message":"parse error","details":null,"hint":null}"""
        }

        assertFailsWith<PostgrestRestException> { runBlocking { remote.pullDecksSince(user, null) } }
    }

    @Test
    fun `una fila sin server_updated_at lanza el mismo error propio`() {
        val (remote, _) = stubRemote { okJson("[${topicRow("t1", serverUpdatedAt = null)}]") }

        val e = assertFailsWith<MissingServerUpdatedAtException> {
            runBlocking { remote.pullTopicsSince(user, null) }
        }

        assertSafeMissingColumnError(e, "topics")
    }

    // --- el push NO envia server_updated_at ----------------------------------------------------------------

    private fun bodyRow(request: io.ktor.client.request.HttpRequestData) = Json.parseToJsonElement(
        (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
    ).jsonArray.single().jsonObject

    @Test
    fun `el cuerpo de las cuatro subidas no lleva server_updated_at pero si updated_at`() = runBlocking {
        val (remote, engine) = stubRemote { okJson("[]") }
        val t = Instant.parse("2026-10-22T10:00:00Z")

        remote.pushDecks(listOf(Deck("d1", "Deck", t, t, null, null, "user-1", null)))
        remote.pushTopics(listOf(Topic("t1", "d1", "Topic", t, t, null, null, "user-1")))
        remote.pushFlashcards(
            listOf(Flashcard("f1", "d1", "Q", "A", t, t, CardType.FLIP, null, null, null, "user-1", null, null))
        )
        remote.pushCardScheduling(listOf(CardScheduling("f1", 2.5, 1, 1, t, t, "user-1", null)))

        assertEquals(4, engine.requests.size)
        for (request in engine.requests) {
            val row = bodyRow(request)
            assertFalse("server_updated_at" in row.keys, "${request.table()} no debe subir server_updated_at: ${row.keys}")
            assertTrue("updated_at" in row.keys, "${request.table()} sigue subiendo el updated_at del cliente")
        }
    }
}
