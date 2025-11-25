package org.braincards.app.domain.models

import kotlinx.datetime.Instant

data class Topic(
    val id: Int = 0,
    val deckId: Int,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant
)