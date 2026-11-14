package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Observa cuantas tarjetas estan pendientes de repaso (alcance "todas", el mismo criterio que la sesion de estudio
 * con StudyScope.All: ver FlashcardDao.observeDueCount). Interfaz aparte, aditiva: no toca FlashcardRepository
 * (igual que PendingChangesObserver).
 *
 * [now] es el reloj con el que se evalua "vencida": Room solo re-emite cuando cambia una tabla, asi que el paso del
 * tiempo (una tarjeta que vence sin que cambie ninguna fila) lo aporta quien llama emitiendo una nueva hora.
 */
interface DueCardsObserver {
    /** Emite el recuento actual y cada vez que CAMBIA (sin repetidos consecutivos), con la ultima hora de [now]. */
    fun observeDueCount(now: Flow<Instant>): Flow<Int>
}
