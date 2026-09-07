package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
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
}
