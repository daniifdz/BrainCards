package org.braincards.app.data.mapper

import org.braincards.app.data.local.entity.TopicEntity
import org.braincards.app.domain.models.Topic

fun TopicEntity.toDomain(): Topic {
    return Topic(
        id = id,
        deckId = deckId,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastSyncedAt = lastSyncedAt,
        userId = userId
    )
}

fun Topic.toEntity(): TopicEntity {
    return TopicEntity(
        id = id,
        deckId = deckId,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastSyncedAt = lastSyncedAt,
        userId = userId
    )
}