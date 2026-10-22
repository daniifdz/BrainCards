package org.braincards.app.domain.repository

import org.braincards.app.domain.models.SyncTable

/**
 * Pregunta minima sobre la BD local para el pull incremental: ¿esta tabla esta VACIA? Una tabla con
 * una marca de agua guardada pero vacia en local significa que la BD se perdio o se vacio sin pasar
 * por el cierre de sesion (p. ej. borrar solo braincards.db): la marca ya no vale y esa tabla debe
 * bajarse entera.
 *
 * "Vacia" cuenta TODAS las filas, tombstones (borrado logico) incluidos: una tabla con solo
 * tombstones NO esta vacia (el pull ya la poblo). Interfaz aparte, no un metodo de
 * LocalDataRepository, para no ensanchar esa interfaz.
 */
interface LocalTableInspector {
    suspend fun isEmpty(table: SyncTable): Boolean
}

/** Valor por defecto del motor: nunca dice "vacia", asi que la guarda de tabla vacia no interviene. */
object NeverEmptyLocalTableInspector : LocalTableInspector {
    override suspend fun isEmpty(table: SyncTable): Boolean = false
}
