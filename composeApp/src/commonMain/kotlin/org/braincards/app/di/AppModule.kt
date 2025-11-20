package org.braincards.app.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.domain.repository.DecksRepository
import org.koin.dsl.module

fun appModule(databaseBuilder: androidx.room.RoomDatabase.Builder<AppDatabase>) = module {

    // Proveer la Base de Datos (Singleton)
    single<AppDatabase> {
        databaseBuilder
            .setDriver(BundledSQLiteDriver()) //necesario para el funcionamineto con KMP
            .setQueryCoroutineContext(kotlinx.coroutines.Dispatchers.IO)
            .build()
    }

    // Proveer el DAO
    single { get<AppDatabase>().deckDao() }

    // Proveer el Repositorio
    single<DecksRepository> { DeckRepositoryImpl(deckDao = get()) }
}