package org.braincards.app.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.RemoteRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Logica pura de la paginacion por cursor (fetchAllByCursor), sin red. */
class CursorPaginationTest {

    private fun ts(second: Int) = Instant.parse("2026-10-22T10:00:%02dZ".format(second))

    private fun row(id: String, second: Int) = RemoteRow(id, PullCursor(ts(second), id))

    @Test
    fun `la primera peticion lleva la marca y las siguientes el cursor de la ultima fila`() = runBlocking {
        val requests = mutableListOf<CursorPageRequest>()
        val watermark = PullCursor(ts(0), "w")

        val rows = fetchAllByCursor(watermark, pageSize = 2) { request ->
            requests += request
            when (requests.size) {
                1 -> listOf(row("a", 1), row("b", 2))
                else -> listOf(row("c", 3))
            }
        }

        assertEquals(listOf("a", "b", "c"), rows.map { it.row })
        assertEquals(CursorPageRequest.First(watermark), requests[0])
        assertEquals(CursorPageRequest.After(PullCursor(ts(2), "b")), requests[1])
        assertEquals(2, requests.size)
    }

    @Test
    fun `sin marca la primera peticion no lleva marca`() = runBlocking {
        val requests = mutableListOf<CursorPageRequest>()

        fetchAllByCursor<String>(null, pageSize = 2) { request ->
            requests += request
            emptyList()
        }

        assertEquals(listOf<CursorPageRequest>(CursorPageRequest.First(null)), requests)
    }

    @Test
    fun `el cursor siguiente es el de la ULTIMA fila del servidor, no un maximo calculado en el cliente`() = runBlocking {
        val requests = mutableListOf<CursorPageRequest>()

        fetchAllByCursor(null, pageSize = 2) { request ->
            requests += request
            // Orden del servidor: el id "10" llega DESPUES del "9" aunque como texto "10" < "9".
            if (requests.size == 1) listOf(row("9", 1), row("10", 1)) else emptyList()
        }

        val next = assertIs<CursorPageRequest.After>(requests[1])
        assertEquals(PullCursor(ts(1), "10"), next.cursor)
    }

    @Test
    fun `una pagina exactamente llena obliga a pedir una mas`() = runBlocking {
        var calls = 0

        fetchAllByCursor(null, pageSize = 2) {
            calls++
            if (calls == 1) listOf(row("a", 1), row("b", 2)) else emptyList()
        }

        assertEquals(2, calls)
    }

    @Test
    fun `una pagina corta termina sin pedir otra`() = runBlocking {
        var calls = 0

        fetchAllByCursor(null, pageSize = 3) {
            calls++
            listOf(row("a", 1), row("b", 2))
        }

        assertEquals(1, calls)
    }

    @Test
    fun `un error en una pagina se propaga`() {
        var calls = 0

        assertFailsWith<IllegalStateException> {
            runBlocking {
                fetchAllByCursor(null, pageSize = 2) {
                    calls++
                    if (calls == 1) listOf(row("a", 1), row("b", 2)) else throw IllegalStateException("boom")
                }
            }
        }
    }

    @Test
    fun `una pagina llena con una fila sin cursor es un error`() {
        assertFailsWith<IllegalStateException> {
            runBlocking {
                fetchAllByCursor(null, pageSize = 1) { listOf(RemoteRow("a", null)) }
            }
        }
    }

    @Test
    fun `pageSize no positivo es un error`() {
        assertFailsWith<IllegalArgumentException> {
            runBlocking { fetchAllByCursor<String>(null, pageSize = 0) { emptyList() } }
        }
    }
}
