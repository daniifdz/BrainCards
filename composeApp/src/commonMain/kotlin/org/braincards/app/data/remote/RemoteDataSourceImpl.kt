package org.braincards.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Instant
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.data.mapper.toDto
import org.braincards.app.data.remote.dto.CardSchedulingDto
import org.braincards.app.data.remote.dto.DeckDto
import org.braincards.app.data.remote.dto.FlashcardDto
import org.braincards.app.data.remote.dto.TopicDto
import org.braincards.app.domain.models.CardScheduling
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

    /**
     * Pull paginado comun a las cuatro tablas: filtra por dueno y marca de agua, y ordena
     * de forma estable y total por (updated_at, clave primaria) para que las paginas de
     * 1000 filas no se solapen ni dejen huecos. La clave primaria es `id` en decks, topics
     * y flashcards, y `card_id` en card_scheduling (no tiene columna id).
     */
    private suspend inline fun <reified D : Any> pullAll(
        table: String,
        primaryKey: String,
        userId: String,
        since: Instant
    ): List<D> = fetchAllPages { from, to ->
        client.from(table)
            .select {
                filter {
                    eq("user_id", userId)
                    gt("updated_at", since.toString())
                }
                order("updated_at", Order.ASCENDING)
                order(primaryKey, Order.ASCENDING)
                range(from, to)
            }
            .decodeList<D>()
    }

    override suspend fun pullDecks(userId: String, since: Instant): List<Deck> {
        return pullAll<DeckDto>("decks", "id", userId, since).map { it.toDomain() }
    }

    override suspend fun pushDecks(decks: List<Deck>) {
        if (decks.isEmpty()) return
        client.from("decks").upsert(decks.map { it.toDto() }) {
            onConflict = "id"
        }
    }

    override suspend fun pullTopics(userId: String, since: Instant): List<Topic> {
        return pullAll<TopicDto>("topics", "id", userId, since).map { it.toDomain() }
    }

    override suspend fun pushTopics(topics: List<Topic>) {
        if (topics.isEmpty()) return
        client.from("topics").upsert(topics.map { it.toDto() }) {
            onConflict = "id"
        }
    }

    override suspend fun pullFlashcards(userId: String, since: Instant): List<Flashcard> {
        return pullAll<FlashcardDto>("flashcards", "id", userId, since).map { it.toDomain() }
    }

    override suspend fun pushFlashcards(flashcards: List<Flashcard>) {
        if (flashcards.isEmpty()) return
        client.from("flashcards").upsert(flashcards.map { it.toDto() }) {
            onConflict = "id"
        }
    }

    override suspend fun pullCardScheduling(userId: String, since: Instant): List<CardScheduling> {
        return pullAll<CardSchedulingDto>("card_scheduling", "card_id", userId, since).map { it.toDomain() }
    }

    // La PK de card_scheduling es card_id (una fila por tarjeta), por eso el conflicto se
    // resuelve sobre card_id y no sobre id.
    override suspend fun pushCardScheduling(schedulings: List<CardScheduling>) {
        if (schedulings.isEmpty()) return
        client.from("card_scheduling").upsert(schedulings.map { it.toDto() }) {
            onConflict = "card_id"
        }
    }
}
