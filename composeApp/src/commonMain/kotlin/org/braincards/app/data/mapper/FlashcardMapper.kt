package org.braincards.app.data.mapper

import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.domain.models.Flashcard

fun FlashcardEntity.toDomain(): Flashcard {
    return Flashcard(
        id = id,
        deckId = deckId,
        topicId = topicId,
        question = question,
        answer = answer,
        createdAt = createdAt,
        updatedAt = updatedAt,
        cardType = cardType,
        deletedAt = deletedAt,
        lastSyncedAt = lastSyncedAt,
        userId = userId,
        options = options,
        correctOptionIndex = correctOptionIndex
    )
}

fun Flashcard.toEntity(): FlashcardEntity {
    return FlashcardEntity(
        id = id,
        deckId = deckId,
        topicId = topicId,
        question = question,
        answer = answer,
        createdAt = createdAt,
        updatedAt = updatedAt,
        cardType = cardType,
        deletedAt = deletedAt,
        lastSyncedAt = lastSyncedAt,
        userId = userId,
        options = options,
        correctOptionIndex = correctOptionIndex
    )
}