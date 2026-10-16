package org.braincards.app.di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import org.koin.core.module.dsl.viewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.braincards.app.config.SUPABASE_ANON_KEY
import org.braincards.app.config.SUPABASE_URL
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.migration.MIGRATION_3_4
import org.braincards.app.data.local.migration.MIGRATION_4_5
import org.braincards.app.data.local.migration.MIGRATION_5_6
import org.braincards.app.data.remote.RemoteDataSourceImpl
import org.braincards.app.data.repository.AuthRepositoryImpl
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.local.TransactionRunner
import org.braincards.app.data.repository.CardSchedulingRepositoryImpl
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.FlashcardRepositoryImpl
import org.braincards.app.data.repository.ReviewLogRepositoryImpl
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.RemoteDataSource
import org.braincards.app.domain.repository.ReviewLogRepository
import org.braincards.app.domain.repository.TopicsRepository
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.ConfirmPasswordResetUseCase
import org.braincards.app.domain.usecase.Auth.DeleteAccountUseCase
import org.braincards.app.domain.usecase.Auth.SendPasswordResetUseCase
import org.braincards.app.domain.usecase.Auth.SignInUseCase
import org.braincards.app.domain.usecase.Auth.SignOutUseCase
import org.braincards.app.domain.usecase.Auth.SignUpUseCase
import org.braincards.app.domain.usecase.Auth.UpdatePasswordUseCase
import org.braincards.app.domain.usecase.Auth.ResendSignUpCodeUseCase
import org.braincards.app.domain.usecase.Auth.VerifyPasswordResetCodeUseCase
import org.braincards.app.domain.usecase.Auth.VerifySignUpCodeUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
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
import org.braincards.app.presentation.screens.auth.AuthViewModel
import org.braincards.app.presentation.screens.decks.DecksViewModel
import org.braincards.app.presentation.screens.flashcards.FlashCardViewModel
import org.braincards.app.presentation.screens.flashcards.TopicVM
import org.braincards.app.presentation.screens.study.StudyViewModel
import org.braincards.app.presentation.screens.settings.ThemeViewModel
import org.braincards.app.presentation.screens.studyStats.StudyStatsVM
import org.braincards.app.presentation.sync.SyncCoordinator
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

fun appModule(
    databaseBuilder: androidx.room.RoomDatabase.Builder<AppDatabase>,
    settingsDataStore: DataStore<Preferences>
) = module {

    single<DataStore<Preferences>> { settingsDataStore }
    viewModel { ThemeViewModel(dataStore = get()) }

    // Cliente de Supabase (Singleton) -- URL/clave via SupabaseConfig.kt generado en build/
    // a partir de local.properties/env vars, nunca hardcodeadas (ver Sprint 12 Bloque B).
    single<SupabaseClient> {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            // Explicito solo por documentacion -- supabase-kt 3.1.4 ya instala este
            // mismo default (10s) aunque no se declare (confirmado en el codigo
            // fuente real, KtorSupabaseHttpClient.kt/SupabaseClientBuilder.kt).
            requestTimeout = 10.seconds
            install(Auth)
            install(Postgrest)
        }
    }
    single<AuthRepository> { AuthRepositoryImpl(client = get()) }

    // Envoltura fina sobre Postgrest (Bloque D) -- ver comentario en RemoteDataSourceImpl.
    single<RemoteDataSource> { RemoteDataSourceImpl(client = get()) }

    factory { SignUpUseCase(repository = get()) }
    factory { SignInUseCase(repository = get()) }
    factory { SignOutUseCase(repository = get()) }
    factory { SendPasswordResetUseCase(repository = get()) }
    factory { VerifyPasswordResetCodeUseCase(repository = get()) }
    factory { UpdatePasswordUseCase(repository = get()) }
    factory { VerifySignUpCodeUseCase(repository = get()) }
    factory { ResendSignUpCodeUseCase(repository = get()) }
    factory {
        ConfirmPasswordResetUseCase(
            verifyPasswordResetCodeUseCase = get(),
            updatePasswordUseCase = get()
        )
    }
    factory {
        BackfillLocalDataUseCase(
            decksRepository = get(),
            topicsRepository = get(),
            flashcardRepository = get()
        )
    }
    factory {
        SyncEngineUseCase(
            authRepository = get(),
            remoteDataSource = get(),
            decksRepository = get(),
            topicsRepository = get(),
            flashcardRepository = get(),
            backfillLocalDataUseCase = get()
        )
    }
    factory {
        DeleteAccountUseCase(
            authRepository = get(),
            decksRepository = get(),
            topicsRepository = get(),
            flashcardRepository = get()
        )
    }

    // single(createdAtStart = true), no viewModel -- ver comentario de cabecera en
    // SyncCoordinator.kt sobre por que no puede ser un ViewModel de Koin aqui.
    // Reutiliza el DataStore<Preferences> ya registrado arriba (settingsDataStore),
    // no crea uno nuevo.
    single(createdAtStart = true) {
        SyncCoordinator(
            authRepository = get(),
            syncEngineUseCase = get(),
            decksRepository = get(),
            topicsRepository = get(),
            flashcardRepository = get(),
            dataStore = get()
        )
    }

    viewModel {
        AuthViewModel(
            authRepository = get(),
            signUpUseCase = get(),
            signInUseCase = get(),
            signOutUseCase = get(),
            sendPasswordResetUseCase = get(),
            confirmPasswordResetUseCase = get(),
            verifySignUpCodeUseCase = get(),
            resendSignUpCodeUseCase = get(),
            deleteAccountUseCase = get(),
            syncCoordinator = get()
        )
    }

    // Proveer la Base de Datos (Singleton)
    single<AppDatabase> {
        databaseBuilder
            .setDriver(BundledSQLiteDriver()) //necesario para el funcionamineto con KMP
            .setQueryCoroutineContext(kotlinx.coroutines.Dispatchers.IO)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }

    // Proveer el DAO
    single { get<AppDatabase>().deckDao() }
    single { get<AppDatabase>().flashcardDao() }
    single {get<AppDatabase>().topicDao()}
    single {get<AppDatabase>().reviewLogDao()}
    single { get<AppDatabase>().cardSchedulingDao() }

    // Proveer el Repositorio
    single<DecksRepository> { DeckRepositoryImpl(deckDao = get(), topicDao = get(), flashcardDao = get()) }
    single<FlashcardRepository> { FlashcardRepositoryImpl(flashcardDao = get()) }
    single<TopicsRepository> { TopicRepositoryImpl(topicDao = get(), flashcardDao = get()) }
    single<ReviewLogRepository> { ReviewLogRepositoryImpl(reviewLogDao = get()) }
    single<TransactionRunner> { RoomTransactionRunner(get<AppDatabase>()) }
    single<CardSchedulingRepository> { CardSchedulingRepositoryImpl(cardSchedulingDao = get(), transactionRunner = get()) }


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
            deleteFlashcardUseCase = get()
        )
    }

    viewModel {
        TopicVM(
            addTopicUseCase = get(),
            getTopicsUseCase = get(),
            updateTopicUseCase = get(),
            deleteTopicUseCase = get()
        )
    }

    //stdy
    factory { GetDueFlashcardsUseCase(repository = get()) }
    factory { ReviewFlashcardUseCase(reviewLogRepository = get(), cardSchedulingRepository = get()) }
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
    factory { GetStudyStatsUseCase(repository = get(), cardSchedulingRepository = get()) }

    viewModel {
        StudyStatsVM(
            getStudyStatsUseCase = get()
        )
    }


}