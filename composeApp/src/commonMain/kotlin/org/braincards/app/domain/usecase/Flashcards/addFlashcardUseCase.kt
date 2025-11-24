package org.braincards.app.domain.usecase.Flashcards

import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FlashcardRepository

class AddFlashcardUseCase(private val repository: FlashcardRepository) {
    suspend operator fun invoke(flashcard: Flashcard) {

        //validaciones
        if (flashcard.question.isBlank() || flashcard.answer.isBlank()) {
            throw IllegalArgumentException("La pregunta y la respuesta no pueden estar vacías")

        }
        repository.addFlashcard(flashcard)
    }
}