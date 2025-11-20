package org.braincards.app.data.local

import androidx.room.RoomDatabase


//esperamos que toda plataforma prove dicha clase, con un metodo para crear la db

expect class DatabaseFactory {
    fun create(): RoomDatabase.Builder<AppDatabase>
}