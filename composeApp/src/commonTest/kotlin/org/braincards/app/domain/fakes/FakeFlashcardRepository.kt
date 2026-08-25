package org.braincards.app.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FlashcardRepository

/**
 * Fake en memoria de FlashcardRepository, sin libreria de mocking.
 * updateFlashcardCalls registra cada llamada a updateFlashcard(...) para poder
 * comprobar en los tests cuantas veces se llamo y con que datos exactos.
 */
class FakeFlashcardRepository : FlashcardRepository {

    private val flashcards = mutableMapOf<String, Flashcard>()
    val updateFlashcardCalls = mutableListOf<Flashcard>()

    override suspend fun addFlashcard(flashcard: Flashcard) {
        flashcards[flashcard.id] = flashcard
    }

    override suspend fun getFlashcardById(id: String): Flashcard? = flashcards[id]

    override fun getFlashcardsByDeckId(deckId: String): Flow<List<Flashcard>> =
        flowOf(flashcards.values.filter { it.deckId == deckId })

    override suspend fun deleteFlashcard(flashcard: Flashcard) {
        flashcards.remove(flashcard.id)
    }

    // "due" ahora depende de card_scheduling (separado de Flashcard) y este fake no
    // modela esa tabla todavia -- stub sin resultados hasta que se retome el wiring
    // de CardScheduling (misma sesion que ReviewFlashcardUseCase/StudyQueue/StudyViewModel).
    // Ningun test que sigue en pie llama a estos dos metodos.
    override suspend fun getDueFlashcardsByDeck(deckId: String, now: Instant): List<Flashcard> =
        emptyList()

    override suspend fun updateFlashcard(flashcard: Flashcard) {
        flashcards[flashcard.id] = flashcard
        updateFlashcardCalls.add(flashcard)
    }

    override fun getAllFlashcards(): Flow<List<Flashcard>> =
        flowOf(flashcards.values.toList())

    override suspend fun getAllDueFlashcards(now: Instant): List<Flashcard> =
        emptyList()
}
