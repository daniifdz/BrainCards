package org.braincards.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.time.ExperimentalTime

@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
        )
    ],
    indices = [Index(value = ["deckId"])]
)
data class TopicEntity @OptIn(ExperimentalTime::class) constructor(
    @PrimaryKey val id: String,
    val deckId: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,


    val deletedAt: Instant? = null,
    val lastSyncedAt: Instant? = null,
    val userId: String? = null

)