package org.braincards.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.braincards.app.data.local.dao.DeckDao
import org.braincards.app.data.local.entity.DeckEntity

@Database(entities = [DeckEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao


}