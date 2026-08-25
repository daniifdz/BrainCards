package org.braincards.app.domain.usecase.Decks

import kotlinx.coroutines.runBlocking
import org.braincards.app.domain.fakes.FakeDecksRepository
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AddDeckUseCaseTest {

    private val repository = FakeDecksRepository()
    private val useCase = AddDeckUseCase(repository)

    @Test
    fun `when name is blank should throw IllegalArgumentException`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            useCase(name = "", description = "Some description")
        }
        
        assertFailsWith<IllegalArgumentException> {
            useCase(name = "   ", description = null)
        }
        Unit
    }

    @Test
    fun `when name is valid should add deck to repository`() = runBlocking {
        val name = "Spanish Vocabulary"
        val description = "Decks for learning Spanish"
        
        useCase(name = name, description = description)
        
        assertTrue(repository.addedDecks.any { it.name == name && it.description == description })
    }
}
