package org.braincards.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.braincards.app.data.local.dao.CardSchedulingDao
import org.braincards.app.data.local.dao.DeckDao
import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.data.local.dao.TopicDao
import org.braincards.app.data.local.dao.ReviewLogDao
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.data.local.entity.DeckEntity

import org.braincards.app.data.local.entity.ReviewLogEntity

import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.local.entity.TopicEntity

@Database(
    entities = [DeckEntity::class, FlashcardEntity::class, TopicEntity::class, ReviewLogEntity::class, CardSchedulingEntity::class],
    version = 6
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao
    abstract fun flashcardDao(): FlashcardDao

    abstract fun topicDao(): TopicDao

    abstract fun reviewLogDao(): ReviewLogDao

    abstract fun cardSchedulingDao(): CardSchedulingDao
}




