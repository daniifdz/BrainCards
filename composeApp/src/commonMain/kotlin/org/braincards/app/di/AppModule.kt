package org.braincards.app.di

import org.koin.core.module.dsl.viewModel
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.migration.MIGRATION_3_4
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.FlashcardRepositoryImpl
import org.braincards.app.data.repository.ReviewLogRepositoryImpl
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.ReviewLogRepository
import org.braincards.app.domain.repository.TopicsRepository
import org.braincards.app.domain.usecase.Decks.AddDeckUseCase
import org.braincards.app.domain.usecase.Decks.GetDeckUseCase
import org.braincards.app.domain.usecase.Decks.UpdateDeckUseCase
import org.braincards.app.domain.usecase.DeleteDeckUseCase
import org.braincards.app.domain.usecase.Flashcards.AddFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.DeleteFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.GetDueFlashcardsUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.ReviewFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.UpdateFlashcardUseCase
import org.braincards.app.domain.usecase.Stats.GetStudyStatsUseCase
import org.braincards.app.domain.usecase.Topics.AddTopicUseCase
import org.braincards.app.domain.usecase.Topics.DeleteTopicUseCase
import org.braincards.app.domain.usecase.Topics.GetTopicsUseCase
import org.braincards.app.domain.usecase.Topics.UpdateTopicUseCase
import org.braincards.app.presentation.screens.decks.DecksViewModel
import org.braincards.app.presentation.screens.flashcards.FlashCardViewModel
import org.braincards.app.presentation.screens.study.StudyViewModel
import org.braincards.app.presentation.screens.studyStats.StudyStatsVM
import org.koin.dsl.module

fun appModule(databaseBuilder: androidx.room.RoomDatabase.Builder<AppDatabase>) = module {

    // Proveer la Base de Datos (Singleton)
    single<AppDatabase> {
        databaseBuilder
            .setDriver(BundledSQLiteDriver()) //necesario para el funcionamineto con KMP
            .setQueryCoroutineContext(kotlinx.coroutines.Dispatchers.IO)
            .addMigrations(MIGRATION_3_4)
            .build()
    }

    // Proveer el DAO
    single { get<AppDatabase>().deckDao() }
    single { get<AppDatabase>().flashcardDao() }
    single {get<AppDatabase>().topicDao()}
    single {get<AppDatabase>().reviewLogDao()}

    // Proveer el Repositorio
    single<DecksRepository> { DeckRepositoryImpl(deckDao = get(), topicDao = get(), flashcardDao = get()) }
    single<FlashcardRepository> { FlashcardRepositoryImpl(flashcardDao = get()) }
    single<TopicsRepository> { TopicRepositoryImpl(topicDao = get(), flashcardDao = get()) }
    single<ReviewLogRepository> { ReviewLogRepositoryImpl(reviewLogDao = get()) }


    //usecasess deck
    factory { AddDeckUseCase(repository = get()) }
    factory { GetDeckUseCase(repository = get()) }
    factory { UpdateDeckUseCase(repository = get()) }
    factory { DeleteDeckUseCase(repository = get()) }

    viewModel {
        DecksViewModel(
            addDeckUseCase = get(),
            getDecksUseCase = get(),
            updateDeckUseCase = get(),
            deleteDeckUseCase = get()
        )
    }

    //usecases flashcard
    factory { AddFlashcardUseCase(repository = get()) }
    factory { GetFlashcardUseCase(repository = get()) }
    factory { UpdateFlashcardUseCase(repository = get()) }
    factory { DeleteFlashcardUseCase(repository = get()) }

    viewModel {
        FlashCardViewModel(
            addFlashcardUseCase = get(),
            getFlashcardUseCase = get(),
            updateFlashcardUseCase = get(),
            deleteFlashcardUseCase = get(),
            //topics
            addTopicUseCase = get(),
            getTopicsUseCase = get(),
            updateTopicUseCase = get(),
            deleteTopicUseCase = get()

        )
    }

    //stdy
    factory { GetDueFlashcardsUseCase(repository = get()) }
    factory { ReviewFlashcardUseCase(repository = get(), reviewLogRepository = get()) }
    viewModel {
        StudyViewModel(
            getDueFlashcardsUseCase = get(),
            reviewFlashcardUseCase = get(),
            getFlashcardUseCase = get(),
        )
    }

    //topics uc
    factory { AddTopicUseCase(repository = get()) }
    factory { GetTopicsUseCase(repository = get()) }
    factory { UpdateTopicUseCase(repository = get()) }
    factory { DeleteTopicUseCase(repository = get()) }

    //Stats
    factory { GetStudyStatsUseCase(repository = get()) }

    viewModel {
        StudyStatsVM(
            getStudyStatsUseCase = get()
        )
    }


}