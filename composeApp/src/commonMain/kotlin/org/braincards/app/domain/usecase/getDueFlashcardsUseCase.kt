package org.braincards.app.domain.usecase

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FlashcardRepository

class GetDueFlashcardsUseCase(private val repository: FlashcardRepository) {
    suspend operator fun invoke(deckId: Int, now: Instant): List<Flashcard> {
        return repository.getDueFlashcardsByDeck(deckId, now)
    }
}