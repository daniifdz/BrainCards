package org.braincards.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "card_scheduling",
    foreignKeys = [
        ForeignKey(
            entity = FlashcardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
        )
    ]
)
data class CardSchedulingEntity(
    @PrimaryKey val cardId: String,
    val easeFactor: Double,
    val interval: Int,
    val repetitions: Int,
    val nextReview: Instant,
    val updatedAt: Instant,

    // Campos de sync, mismo criterio que mazos, temas y tarjetas. Sin valores por defecto a propósito: el compilador señala cada
    // sitio que construye la entidad.
    val userId: String?,
    val lastSyncedAt: Instant?
)
