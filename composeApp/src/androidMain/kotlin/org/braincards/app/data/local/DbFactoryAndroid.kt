package org.braincards.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase


//cumplimos la promesa, uso del Context de Android
actual class DatabaseFactory(
    private val context: Context
) {
    actual fun create(): RoomDatabase.Builder<AppDatabase> {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath("braincards.db")

        return Room.databaseBuilder<AppDatabase>(
            context = appContext,
            name = dbFile.absolutePath
        )
    }
}