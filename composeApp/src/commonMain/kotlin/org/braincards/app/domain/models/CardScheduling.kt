package org.braincards.app.domain.models

import kotlinx.datetime.Instant

data class CardScheduling(
    val cardId: String,
    val easeFactor: Double,
    val interval: Int,
    val repetitions: Int,
    val nextReview: Instant,
    val updatedAt: Instant
)
