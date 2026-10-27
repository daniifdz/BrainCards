package org.braincards.app.util

import io.github.jan.supabase.exceptions.RestException

/**
 * Descripcion de una excepcion APTA PARA LOGS: solo el nombre de la clase y, si es una [RestException],
 * el codigo HTTP. NUNCA `message` ni `toString()`: el `message` de una RestException lleva la URL y las
 * cabeceras de la peticion, `Authorization` (el token de la sesion) incluido.
 */
fun Throwable.safeLogDescription(): String {
    val name = this::class.simpleName ?: "Throwable"
    return if (this is RestException) "$name (HTTP $statusCode)" else name
}

/** Imprime `contexto: Clase` (o `contexto: Clase (HTTP 401)`). Usar siempre en lugar de `println("...: $e")`. */
fun logError(context: String, error: Throwable) {
    println("$context: ${error.safeLogDescription()}")
}
