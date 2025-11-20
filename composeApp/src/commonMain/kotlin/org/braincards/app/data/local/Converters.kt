package org.braincards.app.data.local

import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import kotlin.time.ExperimentalTime

class Converters {
    @OptIn(ExperimentalTime::class)
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.fromEpochMilliseconds(it) }
    }

    @OptIn(ExperimentalTime::class)
    @TypeConverter
    fun dateToTimestamp(date: Instant?): Long? {
        return date?.toEpochMilliseconds()
    }
}