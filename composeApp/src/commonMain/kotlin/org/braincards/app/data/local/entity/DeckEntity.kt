package org.braincards.app.data.local.entity



 import androidx.room.Entity
 import androidx.room.PrimaryKey
 import kotlin.time.ExperimentalTime
 import kotlinx.datetime.Instant

@Entity(tableName = "decks")
 data class DeckEntity @OptIn(ExperimentalTime::class) constructor(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val description: String?
 )


