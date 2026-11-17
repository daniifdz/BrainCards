package org.braincards.app.domain.usecase.Flashcards

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import kotlin.test.Test
import kotlin.test.assertEquals
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
    @Test
    fun un_anverso_en_blanco_marca_solo_el_anverso_y_no_se_anade() = runBlocking<Unit> {
        val e = assertFailsWith<FlashcardFieldException> { useCase(createFlashcard(question = "  ", answer = "A")) }
        assertEquals(setOf(CardField.Question), e.fields)
        assertTrue(repository.getFlashcardById("card-1") == null)
    }

    @Test
    fun un_reverso_en_blanco_marca_solo_el_reverso_y_no_se_anade() = runBlocking<Unit> {
        val e = assertFailsWith<FlashcardFieldException> { useCase(createFlashcard(question = "Q", answer = "")) }
        assertEquals(setOf(CardField.Answer), e.fields)
        assertTrue(repository.getFlashcardById("card-1") == null)
    }

    @Test
    fun los_dos_en_blanco_marcan_los_dos_campos() = runBlocking<Unit> {
        val e = assertFailsWith<FlashcardFieldException> { useCase(createFlashcard(question = "", answer = "")) }
        assertEquals(setOf(CardField.Question, CardField.Answer), e.fields)
    }
}
