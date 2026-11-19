package org.braincards.app.domain.repository

import org.braincards.app.domain.models.SyncTable

/**
 * Pregunta mínima sobre la BD local para el pull incremental: ¿está la tabla vacía? Una tabla con marca de agua guardada
 * pero vacía en local significa que la BD se perdió o se vació sin pasar por el cierre de sesión (p. ej. borrar solo
 * braincards.db): la marca ya no vale y esa tabla se baja entera.
 *
 * «Vacía» cuenta todas las filas, tombstones incluidos: una tabla solo con tombstones no está vacía. Interfaz aparte para
 * no ensanchar LocalDataRepository.
 */
interface LocalTableInspector {
    suspend fun isEmpty(table: SyncTable): Boolean
}

/** Valor por defecto del motor: nunca dice "vacia", asi que la guarda de tabla vacia no interviene. */
object NeverEmptyLocalTableInspector : LocalTableInspector {
    override suspend fun isEmpty(table: SyncTable): Boolean = false
}
