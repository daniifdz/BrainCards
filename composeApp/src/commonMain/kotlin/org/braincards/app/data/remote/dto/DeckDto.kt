package org.braincards.app.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espejo de la tabla `public.decks` de Postgres (sql/supabase_schema.sql).
 * Nombres de columna en snake_case via @SerialName; Instant explicito con
 * InstantIso8601Serializer para que el formato que viaja por Postgrest sea
 * inequivoco (ISO-8601), no el que kotlinx-serialization elija por defecto.
 */
@Serializable
data class DeckDto(
    val id: String,
    val name: String,
    val description: String? = null,
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
