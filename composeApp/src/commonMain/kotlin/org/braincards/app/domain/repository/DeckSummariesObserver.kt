package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.DeckSummary

/**
 * Observa los mazos vivos junto con su recuento de tarjetas vivas, en UNA consulta agregada (ver
 * DeckDao.observeDeckSummaries). Interfaz aparte, aditiva: no toca DecksRepository (igual que DueCardsObserver).
 * Sin orden garantizado: ordena quien consume (ObserveDeckSummariesUseCase).
 */
interface DeckSummariesObserver {
    fun observeDeckSummaries(): Flow<List<DeckSummary>>
}
