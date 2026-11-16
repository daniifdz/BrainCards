package org.braincards.app.domain.usecase.Decks

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.models.Deck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddDeckUseCaseTest {

    private val repository = FakeDecksRepository()
    private val useCase = AddDeckUseCase(repository)

    @Test
    fun `when name is blank should throw DeckNameException Blank and add nothing`() = runBlocking {
        listOf("", "   ").forEach { blank ->
            val e = assertFailsWith<DeckNameException> { useCase(name = blank) }
            assertEquals(DeckNameError.Blank, e.reason)
        }
        assertTrue(repository.addedDecks.isEmpty())
    }

    @Test
    fun `when name is valid should add deck without description and with trimmed name`() = runBlocking {
        useCase(name = "  Spanish Vocabulary ")

        val added = repository.addedDecks.single()
        assertEquals("Spanish Vocabulary", added.name)
        assertNull(added.description)
    }

    @Test
    fun `when a live deck has the same name ignoring case and spaces should throw Duplicate and add nothing`() = runBlocking {
        val now = Clock.System.now()
        repository.addDeck(Deck("existing", "Spanish Vocabulary", now, now, null))
        repository.addedDecks.clear()

        val e = assertFailsWith<DeckNameException> { useCase(name = "  spanish   VOCABULARY") }

        assertEquals(DeckNameError.Duplicate, e.reason)
        assertTrue(repository.addedDecks.isEmpty())
    }
}
