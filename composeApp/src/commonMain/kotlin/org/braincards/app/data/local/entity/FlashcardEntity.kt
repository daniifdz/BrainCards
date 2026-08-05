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

    //metricas de estudio
    val easeFactor: Double,
    val interval: Int,
    val repetitions: Int,
    val cardType: CardType,

    //fechas
    val createdAt: Instant,
    val updatedAt: Instant,
    val nextReview: Instant,

    //sync
    val deletedAt: Instant? = null,
    val lastSyncedAt: Instant? = null,
    val userId: String? = null,

    // tipo test
    val options: List<String>? = null,
    val correctOptionIndex: Int? = null

    )