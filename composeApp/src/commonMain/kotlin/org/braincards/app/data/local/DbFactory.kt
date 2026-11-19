package org.braincards.app.data.local

import androidx.room.RoomDatabase


// Cada plataforma debe proveer esta clase, con un método para crear la base de datos.

expect class DatabaseFactory {
    fun create(): RoomDatabase.Builder<AppDatabase>
}