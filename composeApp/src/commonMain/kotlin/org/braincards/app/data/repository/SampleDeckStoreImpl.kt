package org.braincards.app.data.repository

import org.braincards.app.data.local.TransactionRunner
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.SampleDeckStore
import org.braincards.app.domain.repository.TopicsRepository

/**
 * Guarda mazo, temas y tarjetas en UNA transacción con los repositorios de siempre (`addDeck`, `addTopic`, `addFlashcard`): las
 * mismas filas que el alta manual. Si algo falla a mitad, Room revierte todo y no queda nada.
 */
class SampleDeckStoreImpl(
    private val transactionRunner: TransactionRunner,
    private val decksRepository: DecksRepository,
    private val topicsRepository: TopicsRepository,
    private val flashcardRepository: FlashcardRepository
) : SampleDeckStore {

    override suspend fun insert(deck: Deck, topics: List<Topic>, cards: List<Flashcard>) {
        transactionRunner.inTransaction {
            decksRepository.addDeck(deck)
            topics.forEach { topicsRepository.addTopic(it) }
            cards.forEach { flashcardRepository.addFlashcard(it) }
        }
    }
}
