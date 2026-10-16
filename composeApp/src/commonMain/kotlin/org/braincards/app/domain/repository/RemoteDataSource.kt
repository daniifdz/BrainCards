package org.braincards.app.domain.repository

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic

/**
 * Puente hacia Supabase (Postgrest), en terminos de dominio -- ningun DTO ni
 * detalle de Postgrest se filtra fuera de la implementacion (data/remote/).
 */
interface RemoteDataSource {
    suspend fun pullDecks(userId: String, since: Instant): List<Deck>
    suspend fun pushDecks(decks: List<Deck>)

    suspend fun pullTopics(userId: String, since: Instant): List<Topic>
    suspend fun pushTopics(topics: List<Topic>)

    suspend fun pullFlashcards(userId: String, since: Instant): List<Flashcard>
    suspend fun pushFlashcards(flashcards: List<Flashcard>)

    suspend fun pullCardScheduling(userId: String, since: Instant): List<CardScheduling>
    suspend fun pushCardScheduling(schedulings: List<CardScheduling>)
}
