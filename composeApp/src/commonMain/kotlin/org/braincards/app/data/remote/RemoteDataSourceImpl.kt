package org.braincards.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Instant
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.data.mapper.toDto
import org.braincards.app.data.remote.dto.DeckDto
import org.braincards.app.data.remote.dto.FlashcardDto
import org.braincards.app.data.remote.dto.TopicDto
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.RemoteDataSource

/**
 * Envoltura fina sobre Postgrest -- no atrapa excepciones a proposito. Un fallo de
 * red aqui se propaga tal cual al SyncEngineUseCase (paso siguiente), que es quien
 * decide como manejarlo (por-fila en el PUSH, todo-o-nada por tabla en el PULL,
 * segun quedo acordado en el plan del Bloque D).
 */
class RemoteDataSourceImpl(
    private val client: SupabaseClient
) : RemoteDataSource {

    override suspend fun pullDecks(userId: String, since: Instant): List<Deck> {
        return client.from("decks")
            .select {
                filter {
                    eq("user_id", userId)
                    gt("updated_at", since.toString())
                }
                order("updated_at", Order.ASCENDING)
            }
            .decodeList<DeckDto>()
            .map { it.toDomain() }
    }

    override suspend fun pushDecks(decks: List<Deck>) {
        if (decks.isEmpty()) return
        client.from("decks").upsert(decks.map { it.toDto() }) {
            onConflict = "id"
        }
    }

    override suspend fun pullTopics(userId: String, since: Instant): List<Topic> {
        return client.from("topics")
            .select {
                filter {
                    eq("user_id", userId)
                    gt("updated_at", since.toString())
                }
                order("updated_at", Order.ASCENDING)
            }
            .decodeList<TopicDto>()
            .map { it.toDomain() }
    }

    override suspend fun pushTopics(topics: List<Topic>) {
        if (topics.isEmpty()) return
        client.from("topics").upsert(topics.map { it.toDto() }) {
            onConflict = "id"
        }
    }

    override suspend fun pullFlashcards(userId: String, since: Instant): List<Flashcard> {
        return client.from("flashcards")
            .select {
                filter {
                    eq("user_id", userId)
                    gt("updated_at", since.toString())
                }
                order("updated_at", Order.ASCENDING)
            }
            .decodeList<FlashcardDto>()
            .map { it.toDomain() }
    }

    override suspend fun pushFlashcards(flashcards: List<Flashcard>) {
        if (flashcards.isEmpty()) return
        client.from("flashcards").upsert(flashcards.map { it.toDto() }) {
            onConflict = "id"
        }
    }
}
