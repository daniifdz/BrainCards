package org.braincards.app.data.repository

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.data.fakes.FakeDeckDao
import org.braincards.app.data.fakes.FakeFlashcardDao
import org.braincards.app.data.fakes.FakeTopicDao
import org.braincards.app.data.fakes.FakeTransactionRunner
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.domain.models.Deck
import kotlin.test.Test
import kotlin.test.assertEquals

// LWW por fila en applyRemotePull; la regla exacta está en DeckRepositoryImpl.applyRemotePull.
class DeckRepositoryImplTest {

    private fun localEntity(id: String, updatedAt: Instant, name: String = "Local") = DeckEntity(
        id = id,
        name = name,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = updatedAt,
        description = null,
        userId = "user-1"
    )

    private fun remoteDeck(id: String, updatedAt: Instant, name: String = "Remote") = Deck(
        id = id,
        name = name,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = updatedAt,
        description = null,
        userId = "user-1"
    )

    @Test
    fun `applyRemotePull sobrescribe cuando el remoto es mas nuevo`() = runBlocking {
        val deckDao = FakeDeckDao().apply {
            seed(localEntity(id = "deck-1", updatedAt = Instant.fromEpochMilliseconds(1000)))
        }
        val repository = DeckRepositoryImpl(deckDao, FakeTopicDao(), FakeFlashcardDao(), FakeTransactionRunner())

        repository.applyRemotePull(listOf(remoteDeck(id = "deck-1", updatedAt = Instant.fromEpochMilliseconds(2000))))

        val result = deckDao.get("deck-1")!!
        assertEquals("Remote", result.name)
        assertEquals(Instant.fromEpochMilliseconds(2000), result.updatedAt)
        assertEquals(Instant.fromEpochMilliseconds(2000), result.lastSyncedAt)
    }

    @Test
    fun `applyRemotePull conserva la fila local cuando es mas nueva que el remoto`() = runBlocking {
        val deckDao = FakeDeckDao().apply {
            seed(localEntity(id = "deck-1", updatedAt = Instant.fromEpochMilliseconds(3000)))
        }
        val repository = DeckRepositoryImpl(deckDao, FakeTopicDao(), FakeFlashcardDao(), FakeTransactionRunner())

        repository.applyRemotePull(listOf(remoteDeck(id = "deck-1", updatedAt = Instant.fromEpochMilliseconds(2000))))

        val result = deckDao.get("deck-1")!!
        assertEquals("Local", result.name)
        assertEquals(Instant.fromEpochMilliseconds(3000), result.updatedAt)
        // No se toca -- sigue "sucia" (lastSyncedAt sin actualizar), lista para
        // subirse sola en el push de esta misma pasada de sync().
        assertEquals(null, result.lastSyncedAt)
    }

    @Test
    fun `applyRemotePull inserta la fila cuando no existe local`() = runBlocking {
        val deckDao = FakeDeckDao()
        val repository = DeckRepositoryImpl(deckDao, FakeTopicDao(), FakeFlashcardDao(), FakeTransactionRunner())

        repository.applyRemotePull(listOf(remoteDeck(id = "deck-new", updatedAt = Instant.fromEpochMilliseconds(1000))))

        val result = deckDao.get("deck-new")!!
        assertEquals("Remote", result.name)
        assertEquals(Instant.fromEpochMilliseconds(1000), result.lastSyncedAt)
    }
}
