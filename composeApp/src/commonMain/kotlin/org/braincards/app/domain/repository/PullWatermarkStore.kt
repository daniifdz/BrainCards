package org.braincards.app.domain.repository

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.PullWatermarks
import org.braincards.app.domain.models.SyncTable

/**
 * Donde se guarda la marca de agua del pull incremental, POR USUARIO y por tabla.
 *
 * Contrato (el sync NUNCA debe romperse por esto):
 *  - [load] devuelve null = "sin marca" (se hace pull completo) cuando no hay nada guardado, cuando lo
 *    guardado es de OTRO usuario (se ignora y no se reutiliza), cuando esta corrupto o ilegible, o
 *    cuando el almacenamiento falla. Nunca lanza.
 *  - [saveCursor] / [saveFullPullAt] escriben de forma atomica: actualizar una tabla no pisa el
 *    cursor de otra. Si lo guardado era de otro usuario o estaba corrupto, se empieza de cero para
 *    [userId]. Un fallo de escritura NO lanza (el resultado es "sin marca" en la siguiente lectura).
 *  - Borrarla es cosa de SyncCoordinator.resetSyncState() (cierre de sesion y borrado de cuenta).
 *
 * Quien guarde un cursor debe usar el de la ULTIMA fila que devolvio el servidor (ver
 * RemoteDataSource.pull...Since), nunca un maximo calculado en el cliente.
 */
interface PullWatermarkStore {
    suspend fun load(userId: String): PullWatermarks?
    suspend fun saveCursor(userId: String, table: SyncTable, cursor: PullCursor)
    suspend fun saveFullPullAt(userId: String, at: Instant)
}

/** Implementacion sin efecto: nunca hay marca y no guarda nada (pull completo siempre). */
object NoOpPullWatermarkStore : PullWatermarkStore {
    override suspend fun load(userId: String): PullWatermarks? = null
    override suspend fun saveCursor(userId: String, table: SyncTable, cursor: PullCursor) = Unit
    override suspend fun saveFullPullAt(userId: String, at: Instant) = Unit
}
