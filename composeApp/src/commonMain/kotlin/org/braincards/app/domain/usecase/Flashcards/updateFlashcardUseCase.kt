package org.braincards.app.domain.usecase.Flashcards

import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FlashcardRepository

class UpdateFlashcardUseCase(private val repository: FlashcardRepository) {
    suspend operator fun invoke(flashcard: Flashcard) {
        repository.updateFlashcard(flashcard)
}
}