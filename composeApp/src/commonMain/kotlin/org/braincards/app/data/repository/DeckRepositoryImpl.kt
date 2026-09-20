package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.data.local.dao.DeckDao
import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.data.local.dao.TopicDao
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.data.mapper.toEntity
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository

class DeckRepositoryImpl (private val deckDao: DeckDao, private val topicDao: TopicDao, private val flashcardDao: FlashcardDao) : DecksRepository {
    override suspend fun addDeck(deck: Deck) {
        deckDao.addDeck(deck.toEntity())
    }

    override suspend fun deleteDeck(deck: Deck) {
        val now = Clock.System.now()
        //p1: marcamos deck como borrado
        deckDao.softdeleteDeck(deck.id, now)
        //p2 y p3: todos los topics y los marcamos lo mismo con flashcards
        val topics = topicDao.getTopicsByDeckId(deck.id).first()
        for (topic in topics){
            topicDao.softdeleteTopic(topic.id, now)
        }
        //p4: marcamos todas las flashcards del deck con o sin topic
        val flashcards = flashcardDao.getFlashcardsByDeckId(deck.id).first()
        for (flashcard in flashcards){
            flashcardDao.softdeleteFlashcardById(flashcard.id, now)
        }
    }
    override suspend fun updateDeck(deck: Deck) {
        deckDao.updateDeck(deck.toEntity())
    }

    override fun getAllDecks(): Flow<List<Deck>> {
        return deckDao.getAllDecks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDeckById(id: String): Deck? {
        return deckDao.getDeckById(id)?.toDomain()
    }

    override suspend fun claimOrphanedDecks(userId: String): Int {
        return deckDao.claimOrphanedDecks(userId)
    }

    override suspend fun getPendingSync(userId: String): List<Deck> {
        return deckDao.getPendingSyncDecks(userId).map { it.toDomain() }
    }

    // lastSyncedAt del pull se pisa a updatedAt: el valor que traia el DTO es el
    // de OTRO dispositivo, no dice nada sobre si este dispositivo tiene esta fila
    // pendiente de subir -- justo despues de aplicar un pull, no lo esta.
    override suspend fun applyRemotePull(decks: List<Deck>) {
        val entities = decks.map { it.copy(lastSyncedAt = it.updatedAt).toEntity() }
        deckDao.insertAllDecks(entities)
    }

    override suspend fun markPushed(ids: List<String>, syncedAt: Instant) {
        deckDao.markDecksPushed(ids, syncedAt)
    }
}
