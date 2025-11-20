package org.braincards.app.data.mapper

import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.domain.models.Deck
import kotlin.time.ExperimentalTime


//convertimos del dominio a la base de datos para poder guardar
@OptIn(ExperimentalTime::class)
fun Deck.toEntity(): DeckEntity {
    return DeckEntity(
        id = this.id,
        name = this.name,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        description = this.description


    )
}


//convertimos de la base de datos al dominio
@OptIn(ExperimentalTime::class)
fun DeckEntity.toDomain(): Deck {
    return Deck(
        id = this.id,
        name = this.name,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        description = this.description
    )
}