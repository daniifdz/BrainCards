package org.braincards.app.data.local.entity



 import androidx.room.Entity
 import androidx.room.PrimaryKey
 import kotlin.time.ExperimentalTime
 import kotlinx.datetime.Instant

@Entity(tableName = "decks")
 data class DeckEntity @OptIn(ExperimentalTime::class) constructor(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val description: String?,

    val deletedAt: Instant? = null,
    val userId: String? = null,
    val lastSyncedAt: Instant? = null
 )


