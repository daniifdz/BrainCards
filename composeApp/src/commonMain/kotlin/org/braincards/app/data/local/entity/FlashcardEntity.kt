package org.braincards.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.CardType

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
        )
    ],
    indices = [
        Index(value = ["deckId"]),
        Index(value = ["topicId"])
    ]
)
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val topicId: String? = null,
    val question: String,
    val answer: String,
    val cardType: CardType,

    val createdAt: Instant,
    val updatedAt: Instant,

    val deletedAt: Instant? = null,
    val lastSyncedAt: Instant? = null,
    val userId: String? = null,

    // Reservado para tarjetas de tipo test (sin uso por ahora).
    val options: List<String>? = null,
    val correctOptionIndex: Int? = null

    )