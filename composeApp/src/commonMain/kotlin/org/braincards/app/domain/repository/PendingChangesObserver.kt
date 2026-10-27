package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.PendingStamp

/**
 * Observa los cambios locales pendientes de subir de [userId] (las 4 tablas sincronizables): lo mismo que
 * suman getPendingSync de los cuatro repositorios mas las filas sin dueno (userId NULL). Interfaz aparte,
 * aditiva: no toca las interfaces de repositorio existentes (como LocalTableInspector).
 */
interface PendingChangesObserver {
    /** Emite la huella actual al suscribirse y cada vez que CAMBIA (sin repetidos consecutivos). */
    fun observe(userId: String): Flow<PendingStamp>
}
