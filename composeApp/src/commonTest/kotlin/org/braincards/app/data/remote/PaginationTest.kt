package org.braincards.app.data.remote

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaginationTest {

    /** Fuente falsa: `total` filas numeradas 0 until total, que sirve el rango pedido y anota cada peticion. */
    private class FakeSource(private val total: Int, private val failOnRequest: Int? = null) {
        val requestedRanges = mutableListOf<Pair<Long, Long>>()

        suspend fun page(from: Long, to: Long): List<Int> {
            requestedRanges += from to to
            if (failOnRequest == requestedRanges.size) throw IllegalStateException("fallo simulado en la peticion $failOnRequest")
            return (from..minOf(to, total - 1L)).map { it.toInt() }.filter { it < total }
        }
    }

    @Test
    fun `0 filas hace una sola peticion y devuelve lista vacia`() = runBlocking {
        val source = FakeSource(0)

        val result = fetchAllPages(1000) { from, to -> source.page(from, to) }

        assertEquals(emptyList(), result)
        assertEquals(listOf(0L to 999L), source.requestedRanges)
    }

    @Test
    fun `menos de una pagina se resuelve con una sola peticion`() = runBlocking {
        val source = FakeSource(37)

        val result = fetchAllPages(1000) { from, to -> source.page(from, to) }

        assertEquals((0 until 37).toList(), result)
        assertEquals(1, source.requestedRanges.size)
    }

    @Test
    fun `una pagina exactamente llena obliga a pedir una segunda que viene vacia`() = runBlocking {
        val source = FakeSource(1000)

        val result = fetchAllPages(1000) { from, to -> source.page(from, to) }

        assertEquals((0 until 1000).toList(), result)
        assertEquals(listOf(0L to 999L, 1000L to 1999L), source.requestedRanges)
    }

    @Test
    fun `2500 filas se descargan en 3 paginas sin duplicados ni huecos`() = runBlocking {
        val source = FakeSource(2500)

        val result = fetchAllPages(1000) { from, to -> source.page(from, to) }

        assertEquals((0 until 2500).toList(), result) // mismo orden, ni una repetida ni una perdida
        assertEquals(2500, result.toSet().size)
        assertEquals(listOf(0L to 999L, 1000L to 1999L, 2000L to 2999L), source.requestedRanges)
    }

    @Test
    fun `un error en la pagina 2 se propaga y no se devuelve nada parcial`() = runBlocking {
        val source = FakeSource(2500, failOnRequest = 2)

        val error = assertFailsWith<IllegalStateException> {
            fetchAllPages(1000) { from, to -> source.page(from, to) }
        }

        assertEquals("fallo simulado en la peticion 2", error.message)
        assertEquals(2, source.requestedRanges.size) // no sigue pidiendo tras el error
    }

    @Test
    fun `el tamano de pagina por defecto es 1000`() = runBlocking {
        val source = FakeSource(1)

        fetchAllPages { from, to -> source.page(from, to) }

        assertEquals(listOf(0L to 999L), source.requestedRanges)
    }
}
