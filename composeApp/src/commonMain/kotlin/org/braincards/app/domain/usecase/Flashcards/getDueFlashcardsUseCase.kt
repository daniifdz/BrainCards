package org.braincards.app.domain.usecase.Flashcards

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.domain.repository.FlashcardRepository

class GetDueFlashcardsUseCase(private val repository: FlashcardRepository) {
    suspend operator fun invoke(scope: StudyScope, now: Instant): List<Flashcard> {
        return when (scope) {
            is StudyScope.All -> repository.getAllDueFlashcards(now)
            is StudyScope.Deck -> repository.getDueFlashcardsByDeck(scope.deckId, now)
        }
    }
}