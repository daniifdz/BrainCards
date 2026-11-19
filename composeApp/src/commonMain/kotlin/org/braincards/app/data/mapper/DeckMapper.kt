package org.braincards.app.data.mapper

import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.domain.models.Deck
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
fun Deck.toEntity(): DeckEntity {
    return DeckEntity(
        id = this.id,
        name = this.name,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        description = this.description,
        deletedAt = this.deletedAt,
        userId = this.userId,
        lastSyncedAt = this.lastSyncedAt
    )
}


@OptIn(ExperimentalTime::class)
fun DeckEntity.toDomain(): Deck {
    return Deck(
        id = this.id,
        name = this.name,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        description = this.description,
        deletedAt = this.deletedAt,
        userId = this.userId,
        lastSyncedAt = this.lastSyncedAt
    )
}