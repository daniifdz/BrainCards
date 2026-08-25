package org.braincards.app.domain.models

import kotlinx.datetime.Instant

data class Flashcard(
    val id: String,
    val deckId: String,
    val question: String,
    val answer: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val cardType: CardType,

    // topic opcional: una tarjeta puede no pertenecer a ningún topic
    val topicId: String? = null,

    // sync
    val deletedAt: Instant? = null,
    val lastSyncedAt: Instant? = null,
    val userId: String? = null,

    // tipo test
    val options: List<String>? = null,
    val correctOptionIndex: Int? = null
)
