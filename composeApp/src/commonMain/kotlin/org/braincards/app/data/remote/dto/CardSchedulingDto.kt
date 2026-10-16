package org.braincards.app.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espejo de la tabla `public.card_scheduling` de Postgres (sql/card_scheduling.sql). Los
 * nombres de columna son EXACTAMENTE los de la tabla: card_id, user_id, ease_factor,
 * interval_days (no "interval": palabra delicada en Postgres), repetitions, next_review,
 * updated_at, last_synced_at. Sin valores por defecto: cada campo se rellena a proposito
 * al construirlo (la leccion del bug de mappers que omitian campos en silencio).
 */
@Serializable
data class CardSchedulingDto(
    @SerialName("card_id")
    val cardId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("ease_factor")
    val easeFactor: Double,
    @SerialName("interval_days")
    val intervalDays: Int,
    val repetitions: Int,
    @SerialName("next_review")
    @Serializable(with = InstantIso8601Serializer::class)
    val nextReview: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantIso8601Serializer::class)
    val updatedAt: Instant,
    @SerialName("last_synced_at")
    @Serializable(with = InstantIso8601Serializer::class)
    val lastSyncedAt: Instant?
)
