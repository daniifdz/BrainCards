package org.braincards.app.domain.models

import kotlinx.datetime.Instant

data class Topic(
    val id: String,
    val deckId: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val lastSyncedAt: Instant? = null,
    val userId: String? = null
)


