package org.braincards.app.di

import org.koin.core.module.dsl.viewModel
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.usecase.AddDeckUseCase
import org.braincards.app.domain.usecase.GetDeckUseCase
import org.braincards.app.presentation.screens.decks.DecksViewModel
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

    //usecasess
    factory { AddDeckUseCase(repository = get()) }
    factory { GetDeckUseCase(repository = get()) }


    viewModel {
        DecksViewModel(
            addDeckUseCase = get(),
            getDecksUseCase = get()
        )
    }

}