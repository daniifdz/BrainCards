package org.braincards.app.domain.repository

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.PullWatermarks
import org.braincards.app.domain.models.SyncTable

/**
 * Dónde se guarda la marca de agua del pull incremental, por usuario y por tabla.
 *
 * Contrato (el sync nunca debe romperse por esto):
 * - [load] devuelve null («sin marca», se hace pull completo) cuando no hay nada guardado, lo guardado es de otro usuario,
 *   está corrupto o ilegible, o el almacenamiento falla. Nunca lanza.
 * - [saveCursor] y [saveFullPullAt] escriben de forma atómica: actualizar una tabla no pisa el cursor de otra. Si lo
 *   guardado era de otro usuario o estaba corrupto, se empieza de cero para [userId]. Un fallo de escritura no lanza (la
 *   siguiente lectura devuelve «sin marca»).
 * - Borrarla es cosa de SyncCoordinator.resetSyncState() (cierre de sesión y borrado de cuenta).
 *
 * Quien guarde un cursor debe usar el de la última fila que devolvió el servidor, nunca un máximo calculado en el cliente.
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
