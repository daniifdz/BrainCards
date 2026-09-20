package org.braincards.app.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.braincards.app.domain.models.CardType

/**
 * Espejo de la tabla `public.flashcards` de Postgres (sql/supabase_schema.sql).
 * Mismo patron que DeckDto/TopicDto. `cardType` usa CardType directamente --
 * kotlinx-serialization serializa un enum por su nombre por defecto, que es
 * exactamente lo que el CHECK de Postgres exige ('FLIP', 'MULTIPLE_CHOICE'),
 * asi que no hace falta un serializador a medida. `options` viaja como
 * List<String>? y Postgrest lo mapea directo a la columna jsonb.
 */
@Serializable
data class FlashcardDto(
    val id: String,
    @SerialName("deck_id")
    val deckId: String,
    @SerialName("topic_id")
    val topicId: String? = null,
    val question: String,
    val answer: String,
    @SerialName("card_type")
    val cardType: CardType,
    val options: List<String>? = null,
    @SerialName("correct_option_index")
    val correctOptionIndex: Int? = null,
    @SerialName("created_at")
    @Serializable(with = InstantIso8601Serializer::class)
    val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantIso8601Serializer::class)
    val updatedAt: Instant,
    @SerialName("deleted_at")
    @Serializable(with = InstantIso8601Serializer::class)
    val deletedAt: Instant? = null,
    @SerialName("last_synced_at")
    @Serializable(with = InstantIso8601Serializer::class)
    val lastSyncedAt: Instant? = null,
    @SerialName("user_id")
    val userId: String
)
