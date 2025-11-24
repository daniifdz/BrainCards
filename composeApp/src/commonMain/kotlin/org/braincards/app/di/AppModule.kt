package org.braincards.app.di

import org.koin.core.module.dsl.viewModel
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.FlashcardRepositoryImpl
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.usecase.AddDeckUseCase
import org.braincards.app.domain.usecase.AddFlashcardUseCase
import org.braincards.app.domain.usecase.GetDeckUseCase
import org.braincards.app.domain.usecase.GetDueFlashcardsUseCase
import org.braincards.app.domain.usecase.GetFlashcardUseCase
import org.braincards.app.domain.usecase.ReviewFlashcardUseCase
import org.braincards.app.presentation.screens.decks.DecksViewModel
import org.braincards.app.presentation.screens.flashcards.FlashCardViewModel
import org.braincards.app.presentation.screens.study.StudyViewModel
import org.koin.dsl.module

fun appModule(databaseBuilder: androidx.room.RoomDatabase.Builder<AppDatabase>) = module {

    // Proveer la Base de Datos (Singleton)
    single<AppDatabase> {
        databaseBuilder
            .setDriver(BundledSQLiteDriver()) //necesario para el funcionamineto con KMP
            .setQueryCoroutineContext(kotlinx.coroutines.Dispatchers.IO)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .fallbackToDestructiveMigration(true)
            .build()
    }

    // Proveer el DAO
    single { get<AppDatabase>().deckDao() }
    single { get<AppDatabase>().flashcardDao() }

    // Proveer el Repositorio
    single<DecksRepository> { DeckRepositoryImpl(deckDao = get()) }
    single<FlashcardRepository> { FlashcardRepositoryImpl(flashcardDao = get()) }

    //usecasess deck
    factory { AddDeckUseCase(repository = get()) }
    factory { GetDeckUseCase(repository = get()) }

    viewModel {
        DecksViewModel(
            addDeckUseCase = get(),
            getDecksUseCase = get()
        )
    }

    //usecases flashcard
    factory { AddFlashcardUseCase(repository = get()) }
    factory { GetFlashcardUseCase(repository = get()) }


    viewModel {
        FlashCardViewModel(
            addFlashcardUseCase = get(),
            getFlashcardUseCase = get()


        )
    }

    //stdy
    factory { GetDueFlashcardsUseCase(repository = get()) }
    factory { ReviewFlashcardUseCase(repository = get()) }
    viewModel {
        StudyViewModel(
            getDueFlashcardsUseCase = get(),
            reviewFlashcardUseCase = get()
        )
    }
}
