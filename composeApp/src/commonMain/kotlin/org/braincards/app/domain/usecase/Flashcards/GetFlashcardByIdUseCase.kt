package org.braincards.app.domain.usecase.Flashcards

import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FlashcardRepository

/** La tarjeta VIVA con ese id (null si no existe o esta borrada). */
class GetFlashcardByIdUseCase(private val repository: FlashcardRepository) {
    suspend operator fun invoke(id: String): Flashcard? = repository.getFlashcardById(id)
}
