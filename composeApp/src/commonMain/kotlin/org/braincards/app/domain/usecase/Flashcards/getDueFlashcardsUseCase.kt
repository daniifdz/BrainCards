package org.braincards.app.domain.usecase.Flashcards

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FlashcardRepository

class GetDueFlashcardsUseCase(private val repository: FlashcardRepository) {
    suspend operator fun invoke(deckId: Int, now: Instant): List<Flashcard> {
        return if (deckId == -1) {
            repository.getAllDueFlashcards(now)
        } else {
            repository.getDueFlashcardsByDeck(deckId, now)
        }
    }
}