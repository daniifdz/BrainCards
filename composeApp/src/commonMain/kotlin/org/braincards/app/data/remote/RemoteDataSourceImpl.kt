package org.braincards.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
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
import org.braincards.app.domain.models.MissingServerUpdatedAtException
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.RemoteRow
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.RemoteDataSource

/**
 * Envoltura fina sobre Postgrest. No atrapa excepciones a propósito: un fallo de red se propaga tal cual a
 * SyncEngineUseCase, que decide cómo manejarlo (por fila en el push, todo o nada por tabla en el pull).
 */
class RemoteDataSourceImpl(
    private val client: SupabaseClient,
    // Tamano de pagina del pull por cursor (los pull... completos usan DEFAULT_PAGE_SIZE). Parametro
    // solo para poder probar la paginacion con paginas pequenas; en produccion, el valor por defecto.
    private val pageSize: Int = DEFAULT_PAGE_SIZE
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

    /**
     * Pull incremental comun a las cuatro tablas (ver RemoteDataSource.pull...Since): filtra por
     * dueno, ordena por `(server_updated_at, clave)` y pagina por CURSOR, sin offset.
     *  - Primera pagina con marca: `server_updated_at >= marca - PULL_OVERLAP_MARGIN`. Sin marca: sin
     *    filtro.
     *  - Siguientes: `or(ts > ultimo, and(ts = ultimo, clave > ultimo id))`, estricto, con el cursor de
     *    la ULTIMA fila devuelta por el servidor.
     * Todos los timestamps de los filtros van en UTC con sufijo Z (Instant.toString()), nunca +00:00.
     *
     * Si el servidor no tiene la columna (PostgREST 400 con codigo 42703) o una fila llega sin ella,
     * lanza MissingServerUpdatedAtException SIN causa: el message de una RestException incluye las
     * cabeceras de la peticion (Authorization) y no debe viajar en el error.
     */
    private suspend inline fun <reified D : Any, T> pullSince(
        table: String,
        primaryKey: String,
        userId: String,
        watermark: PullCursor?,
        crossinline idOf: (D) -> String,
        crossinline stampOf: (D) -> Instant?,
        crossinline toDomain: (D) -> T
    ): List<RemoteRow<T>> = fetchAllByCursor(watermark, pageSize) { request ->
        val dtos = try {
            client.from(table)
                .select {
                    filter {
                        eq("user_id", userId)
                        when (request) {
                            is CursorPageRequest.First -> {
                                val start = request.watermark
                                if (start != null) {
                                    gte(SERVER_UPDATED_AT, (start.serverUpdatedAt - PULL_OVERLAP_MARGIN).toString())
                                }
                            }
                            is CursorPageRequest.After -> {
                                val ts = request.cursor.serverUpdatedAt.toString()
                                or {
                                    gt(SERVER_UPDATED_AT, ts)
                                    and {
                                        eq(SERVER_UPDATED_AT, ts)
                                        gt(primaryKey, request.cursor.id)
                                    }
                                }
                            }
                        }
                    }
                    order(SERVER_UPDATED_AT, Order.ASCENDING)
                    order(primaryKey, Order.ASCENDING)
                    limit(pageSize.toLong())
                }
                .decodeList<D>()
        } catch (e: PostgrestRestException) {
            if (e.statusCode == 400 && e.code == POSTGRES_UNDEFINED_COLUMN) throw MissingServerUpdatedAtException(table)
            throw e
        }
        dtos.map { dto ->
            val stamp = stampOf(dto) ?: throw MissingServerUpdatedAtException(table)
            RemoteRow(toDomain(dto), PullCursor(stamp, idOf(dto)))
        }
    }

    override suspend fun pullDecksSince(userId: String, watermark: PullCursor?): List<RemoteRow<Deck>> =
        pullSince<DeckDto, Deck>("decks", "id", userId, watermark, { it.id }, { it.serverUpdatedAt }, { it.toDomain() })

    override suspend fun pullTopicsSince(userId: String, watermark: PullCursor?): List<RemoteRow<Topic>> =
        pullSince<TopicDto, Topic>("topics", "id", userId, watermark, { it.id }, { it.serverUpdatedAt }, { it.toDomain() })

    override suspend fun pullFlashcardsSince(userId: String, watermark: PullCursor?): List<RemoteRow<Flashcard>> =
        pullSince<FlashcardDto, Flashcard>("flashcards", "id", userId, watermark, { it.id }, { it.serverUpdatedAt }, { it.toDomain() })

    override suspend fun pullCardSchedulingSince(userId: String, watermark: PullCursor?): List<RemoteRow<CardScheduling>> =
        pullSince<CardSchedulingDto, CardScheduling>(
            "card_scheduling", "card_id", userId, watermark, { it.cardId }, { it.serverUpdatedAt }, { it.toDomain() }
        )

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

// Columna de la marca de agua de servidor (sql/server_updated_at.sql).
private const val SERVER_UPDATED_AT = "server_updated_at"

// Codigo SQLSTATE de Postgres para "columna inexistente" (undefined_column).
private const val POSTGRES_UNDEFINED_COLUMN = "42703"
