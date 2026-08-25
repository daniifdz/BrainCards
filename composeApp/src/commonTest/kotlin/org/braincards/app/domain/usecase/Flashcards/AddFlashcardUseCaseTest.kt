package org.braincards.app.domain.usecase.Flashcards

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AddFlashcardUseCaseTest {

    private val repository = FakeFlashcardRepository()
    private val useCase = AddFlashcardUseCase(repository)

    private fun createFlashcard(question: String, answer: String) = Flashcard(
        id = "card-1",
        deckId = "deck-1",
        question = question,
        answer = answer,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
        nextReview = Clock.System.now(),
        interval = 0,
        repetitions = 0,
        cardType = CardType.FLIP
    )

    @Test
    fun `when question is blank should throw IllegalArgumentException`() = runBlocking {
        val flashcard = createFlashcard(question = "", answer = "Answer")
        assertFailsWith<IllegalArgumentException> {
            useCase(flashcard)
        }
        Unit
    }

    @Test
    fun `when answer is blank should throw IllegalArgumentException`() = runBlocking {
        val flashcard = createFlashcard(question = "Question", answer = "  ")
        assertFailsWith<IllegalArgumentException> {
            useCase(flashcard)
        }
        Unit
    }

    @Test
    fun `when flashcard is valid should add it to repository`() = runBlocking {
        val flashcard = createFlashcard(question = "Hello", answer = "Hola")
        
        useCase(flashcard)
        
        val retrieved = repository.getFlashcardById(flashcard.id)
        assertTrue(retrieved == flashcard)
    }
}
