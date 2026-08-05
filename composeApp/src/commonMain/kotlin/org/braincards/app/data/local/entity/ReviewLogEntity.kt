package org.braincards.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.ExperimentalTime
import kotlinx.datetime.Instant
import androidx.room.Index


@Entity(
    tableName = "reviewlog",
    indices = [
        Index(value = ["cardId"]),
        Index(value = ["userId"])
    ]

)

data class  ReviewLogEntity @OptIn(ExperimentalTime::class) constructor(
    @PrimaryKey val id: String,
    val cardId: String,
    val timestamp: Instant,
    val grade: Int,
    val previousInterval: Int,
    val newInterval: Int,


    val userId:String? = null,



    )