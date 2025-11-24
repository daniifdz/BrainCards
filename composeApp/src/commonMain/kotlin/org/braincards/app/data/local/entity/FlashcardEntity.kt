package org.braincards.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["deckId"])]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val deckId: Int,
    val question: String,
    val answer: String,

    //metricas de estudio
    val easeFactor: Double,
    val interval: Int,
    val repetitions: Int,

    //fechas
    val createdAt: Instant,
    val updatedAt: Instant,
    val nextReview: Instant
)