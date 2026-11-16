package org.braincards.app.domain.usecase.Decks

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.models.Deck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateDeckUseCaseTest {

    private val repository = FakeDecksRepository()
    private val useCase = UpdateDeckUseCase(repository)
    private val t = Instant.fromEpochMilliseconds(1_000)

    private fun deck(id: String, name: String, description: String? = null) = Deck(id, name, t, t, description)

    @Test
    fun `renaming to a free name saves it trimmed and keeps the rest of the fields`() = runBlocking {
        repository.addDeck(deck("1", "Uno", description = "se conserva"))

        useCase(deck("1", "  Nuevo  ", description = "se conserva"))

        val saved = repository.getDeckById("1")!!
        assertEquals("Nuevo", saved.name)
        assertEquals("se conserva", saved.description)
    }

    @Test
    fun `renaming to another live deck name throws Duplicate and saves nothing`() = runBlocking {
        repository.addDeck(deck("1", "Uno"))
        repository.addDeck(deck("2", "Dos"))

        val e = assertFailsWith<DeckNameException> { useCase(deck("2", " uno ")) }

        assertEquals(DeckNameError.Duplicate, e.reason)
        assertEquals("Dos", repository.getDeckById("2")!!.name)
    }

    @Test
    fun `changing only the case of its own name is allowed (excludes itself by id)`() = runBlocking {
        repository.addDeck(deck("1", "vocab"))

        useCase(deck("1", "Vocab"))

        assertEquals("Vocab", repository.getDeckById("1")!!.name)
    }

    @Test
    fun `saving an unchanged name is allowed even if a duplicate already exists (arrived by sync)`() = runBlocking {
        repository.addDeck(deck("1", "Repetido"))
        repository.addDeck(deck("2", "repetido"))

        useCase(deck("2", "repetido"))

        assertEquals("repetido", repository.getDeckById("2")!!.name)
    }

    @Test
    fun `a blank name throws Blank`() = runBlocking {
        repository.addDeck(deck("1", "Uno"))

        val e = assertFailsWith<DeckNameException> { useCase(deck("1", "   ")) }

        assertEquals(DeckNameError.Blank, e.reason)
        assertEquals("Uno", repository.getDeckById("1")!!.name)
    }
}
