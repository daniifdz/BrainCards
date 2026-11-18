package org.braincards.app.domain.usecase.Flashcards

import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.domain.repository.FreeReviewCardsSource

/**
 * Cola del repaso libre: TODAS las tarjetas activas del [StudyScope] (no solo las pendientes) en orden aleatorio. Parte de una base
 * ORDENADA por id (no depende del orden en que las devuelva la fuente) y la baraja con la semilla: asi el orden es reproducible.
 */
class GetFreeReviewCardsUseCase(
    private val source: FreeReviewCardsSource,
    private val shuffler: CardShuffler = SeededCardShuffler
) {
    suspend operator fun invoke(scope: StudyScope, seed: Long): List<Flashcard> {
        val all = when (scope) {
            is StudyScope.All -> source.getAll()
            is StudyScope.Deck -> source.getByDeck(scope.deckId)
        }
        return shuffler.shuffle(all.sortedBy { it.id }, seed)
    }
}
