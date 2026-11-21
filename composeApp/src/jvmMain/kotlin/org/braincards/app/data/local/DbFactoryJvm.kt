package org.braincards.app.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual class DatabaseFactory {
    actual fun create(): RoomDatabase.Builder<AppDatabase> {
        // almaceno db en la carpeta del user
        val dbFile = File(appDataDir(), "braincards.db")
        return Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
        )
    }
}