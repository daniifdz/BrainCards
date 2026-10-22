package org.braincards.app.domain.repository

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.RemoteRow
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

    // --- Pull incremental (marca de agua de servidor) -------------------------------------------
    // Cambios ADITIVOS: el motor todavia no los usa (sigue con los pull... completos de arriba).
    //
    // Devuelven las filas del usuario ordenadas por (server_updated_at, clave) con su cursor:
    //  - watermark == null: desde el principio (pull completo).
    //  - watermark != null: la PRIMERA pagina pide server_updated_at >= watermark - 2 minutos (solape
    //    para carreras de commits; el aplicado es idempotente por el LWW) y las siguientes usan el
    //    cursor ESTRICTO de la ultima fila: (ts, id) > (ultimo ts, ultimo id).
    // Los tombstones (deleted_at) se devuelven como cualquier otra fila.
    //
    // Implementacion por defecto: delega en el pull completo y devuelve cursores null (nadie puede
    // avanzar una marca con ellos), asi que fakes e implementaciones antiguas siguen valiendo.
    // Si el servidor no tiene server_updated_at, la implementacion real lanza
    // MissingServerUpdatedAtException (error propio, sin message de RestException).

    suspend fun pullDecksSince(userId: String, watermark: PullCursor?): List<RemoteRow<Deck>> =
        pullDecks(userId, FULL_PULL_SINCE).map { RemoteRow(it, null) }

    suspend fun pullTopicsSince(userId: String, watermark: PullCursor?): List<RemoteRow<Topic>> =
        pullTopics(userId, FULL_PULL_SINCE).map { RemoteRow(it, null) }

    suspend fun pullFlashcardsSince(userId: String, watermark: PullCursor?): List<RemoteRow<Flashcard>> =
        pullFlashcards(userId, FULL_PULL_SINCE).map { RemoteRow(it, null) }

    suspend fun pullCardSchedulingSince(userId: String, watermark: PullCursor?): List<RemoteRow<CardScheduling>> =
        pullCardScheduling(userId, FULL_PULL_SINCE).map { RemoteRow(it, null) }
}

// "Desde siempre", mismo valor que SyncEngineUseCase.PULL_SINCE_EPOCH.
private val FULL_PULL_SINCE: Instant = Instant.fromEpochMilliseconds(0)
