package org.braincards.app.domain.models

import kotlinx.datetime.Instant

/**
 * Posicion de una fila en el orden del pull incremental: `(server_updated_at, clave)`. La marca la
 * escribe el SERVIDOR (trigger de sql/server_updated_at.sql), no el reloj del cliente. [id] es la
 * clave de desempate (`id`; `card_id` en card_scheduling) para que dos filas con la misma marca
 * tengan un orden total y estable.
 *
 * Se guarda como [Instant] (precision de nanosegundos, suficiente para los microsegundos de
 * Postgres): al volver a escribirlo en UTC con sufijo Z representa EXACTAMENTE el mismo instante.
 */
data class PullCursor(val serverUpdatedAt: Instant, val id: String)

/**
 * Una fila descargada junto con su [cursor]. [cursor] es null solo en la implementacion por defecto
 * de RemoteDataSource.pull...Since (que delega en el pull completo y no conoce la marca del servidor).
 */
data class RemoteRow<T>(val row: T, val cursor: PullCursor?)

/**
 * El servidor no devuelve `server_updated_at`: falta aplicar sql/server_updated_at.sql. Error PROPIO
 * y sin causa a proposito: NO arrastra el `message` de ninguna RestException (supabase-kt lo compone
 * con las cabeceras de la peticion, incluido el Authorization). Solo lleva el nombre de la tabla.
 */
class MissingServerUpdatedAtException(val table: String) : IllegalStateException(
    "La tabla $table no tiene server_updated_at en el servidor: falta aplicar sql/server_updated_at.sql en Supabase"
)
