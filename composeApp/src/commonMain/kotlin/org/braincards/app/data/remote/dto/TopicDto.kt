package org.braincards.app.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espejo de la tabla `public.topics` de Postgres (sql/supabase_schema.sql).
 * Mismo patron que DeckDto: snake_case via @SerialName, Instant explicito
 * con InstantIso8601Serializer.
 */
@Serializable
data class TopicDto(
    val id: String,
    @SerialName("deck_id")
    val deckId: String,
    val name: String,
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
