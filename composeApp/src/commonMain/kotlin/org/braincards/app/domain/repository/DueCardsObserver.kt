package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Observa cuántas tarjetas están pendientes de repaso (alcance «todas», mismo criterio que la sesión de estudio con
 * StudyScope.All: ver FlashcardDao.observeDueCount). Interfaz aparte, aditiva: no toca FlashcardRepository.
 *
 * [now] es el reloj con el que se evalúa «vencida»: Room solo re-emite cuando cambia una tabla, así que el paso del tiempo
 * (una tarjeta que vence sin que cambie ninguna fila) lo aporta quien llama emitiendo una hora nueva.
 */
interface DueCardsObserver {
    /** Emite el recuento actual y cada vez que CAMBIA (sin repetidos consecutivos), con la ultima hora de [now]. */
    fun observeDueCount(now: Flow<Instant>): Flow<Int>
}
