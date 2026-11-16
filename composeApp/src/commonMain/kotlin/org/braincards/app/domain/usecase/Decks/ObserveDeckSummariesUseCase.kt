package org.braincards.app.domain.usecase.Decks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.braincards.app.domain.models.DeckSummary
import org.braincards.app.domain.repository.DeckSummariesObserver

/** Mazos vivos con su recuento de tarjetas, ya en el orden fijo de la lista ([deckSummaryComparator]). */
class ObserveDeckSummariesUseCase(private val observer: DeckSummariesObserver) {
    operator fun invoke(): Flow<List<DeckSummary>> =
        observer.observeDeckSummaries().map { it.sortedWith(deckSummaryComparator) }
}
