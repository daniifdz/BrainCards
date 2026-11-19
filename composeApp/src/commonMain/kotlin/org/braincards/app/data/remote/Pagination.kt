package org.braincards.app.data.remote

import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.RemoteRow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Tamano de pagina del pull: el max-rows por defecto de PostgREST/Supabase es 1000. */
const val DEFAULT_PAGE_SIZE = 1000

/**
 * Descarga todas las filas de una consulta paginada. [fetchPage] recibe el rango inclusivo `[from, to]` (traducido a
 * offset/limit por supabase-kt) y devuelve esa página. Se sigue pidiendo hasta que una página llega con menos de [pageSize]
 * filas, así que una página exactamente llena obliga a pedir una más (que vendrá vacía).
 *
 * La consulta debe tener un orden estable y total (updated_at + clave primaria); si no, las páginas pueden solaparse o
 * dejar huecos entre peticiones.
 *
 * Un error en cualquier página se propaga tal cual: el pull de la tabla se aborta entero (todo o nada por tabla).
 *
 * Supuesto: el servidor devuelve páginas de [pageSize] filas cuando hay más. Si el max-rows de Supabase se configurase por
 * debajo de [pageSize], una página corta parecería la última.
 */
suspend fun <T> fetchAllPages(
    pageSize: Int = DEFAULT_PAGE_SIZE,
    fetchPage: suspend (from: Long, to: Long) -> List<T>
): List<T> {
    require(pageSize > 0) { "pageSize debe ser positivo" }
    val all = mutableListOf<T>()
    var from = 0L
    while (true) {
        val page = fetchPage(from, from + pageSize - 1)
        all += page
        if (page.size < pageSize) return all
        from += pageSize
    }
}

/**
 * Solape de la PRIMERA pagina del pull incremental: se pide `server_updated_at >= marca - 2 minutos`
 * en vez de `>= marca`. Cubre las carreras de commits (una transaccion que empezo antes puede
 * confirmar despues de que otra con marca mayor ya se bajo); lo que se vuelve a bajar es idempotente
 * porque el LWW del aplicado ignora lo que no es mas nuevo.
 */
val PULL_OVERLAP_MARGIN: Duration = 2.minutes

/** Que pagina pide el pull por cursor. */
sealed interface CursorPageRequest {
    /** Primera pagina: `server_updated_at >= watermark - PULL_OVERLAP_MARGIN`, o sin filtro si es null. */
    data class First(val watermark: PullCursor?) : CursorPageRequest

    /** Paginas siguientes: cursor ESTRICTO `(ts, id) > (cursor.ts, cursor.id)`. */
    data class After(val cursor: PullCursor) : CursorPageRequest
}

/**
 * Descarga TODAS las filas de una consulta paginada POR CURSOR (sin offset). A diferencia de
 * [fetchAllPages], es seguro cuando las filas cambian mientras se descarga: una fila modificada solo
 * cambia de posicion en el orden `(server_updated_at, clave)` y se recoge mas adelante; con offset
 * desplazaria las siguientes y alguna se perderia para siempre (con un pull incremental nadie la
 * volveria a pedir).
 *
 * [fetchPage] recibe [CursorPageRequest.First] la primera vez y [CursorPageRequest.After] con el
 * cursor de la ULTIMA fila que devolvio el servidor despues. Se corta cuando una pagina llega con
 * menos de [pageSize] filas: una pagina exactamente llena obliga a pedir una mas (que vendra vacia).
 * Un error en cualquier pagina se propaga tal cual (todo o nada por tabla).
 */
suspend fun <T> fetchAllByCursor(
    watermark: PullCursor?,
    pageSize: Int = DEFAULT_PAGE_SIZE,
    fetchPage: suspend (CursorPageRequest) -> List<RemoteRow<T>>
): List<RemoteRow<T>> {
    require(pageSize > 0) { "pageSize debe ser positivo" }
    val all = mutableListOf<RemoteRow<T>>()
    var request: CursorPageRequest = CursorPageRequest.First(watermark)
    while (true) {
        val page = fetchPage(request)
        all += page
        if (page.size < pageSize) return all
        val lastCursor = checkNotNull(page.last().cursor) { "Una pagina por cursor devolvio una fila sin cursor" }
        request = CursorPageRequest.After(lastCursor)
    }
}
