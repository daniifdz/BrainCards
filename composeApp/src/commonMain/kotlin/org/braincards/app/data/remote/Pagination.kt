package org.braincards.app.data.remote

/** Tamano de pagina del pull: el max-rows por defecto de PostgREST/Supabase es 1000. */
const val DEFAULT_PAGE_SIZE = 1000

/**
 * Descarga TODAS las filas de una consulta paginada. [fetchPage] recibe el rango inclusivo
 * `[from, to]` (traducido a offset/limit por supabase-kt) y devuelve esa pagina. Se sigue
 * pidiendo hasta que una pagina llega con menos de [pageSize] filas, asi que una pagina
 * exactamente llena obliga a pedir una mas (que vendra vacia).
 *
 * La consulta que se pase DEBE tener un orden estable y total (updated_at + clave primaria),
 * si no las paginas pueden solaparse o dejar huecos entre peticiones.
 *
 * Un error en cualquier pagina se propaga tal cual: el pull de la tabla se aborta entero
 * (mismo criterio todo-o-nada por tabla que ya tenia el pull sin paginar).
 *
 * Supuesto: el servidor devuelve paginas de [pageSize] filas cuando hay mas. Si el max-rows de
 * Supabase se configurase por DEBAJO de [pageSize], una pagina corta pareceria la ultima.
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
