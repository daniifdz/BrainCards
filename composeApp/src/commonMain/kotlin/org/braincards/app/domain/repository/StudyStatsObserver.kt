package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.StudyStats

/**
 * Observa la madurez de las tarjetas activas en UNA consulta agregada (ver FlashcardDao.observeMaturityCounts). Interfaz
 * aparte, aditiva: no toca FlashcardRepository (igual que DueCardsObserver y DeckSummariesObserver).
 */
interface StudyStatsObserver {
    /** Emite el recuento actual y cada vez que CAMBIA (sin repetidos consecutivos). */
    fun observeStudyStats(): Flow<StudyStats>
}
