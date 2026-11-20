package org.braincards.app.di

import org.braincards.app.data.remote.SupabaseFailureClassifier
import org.braincards.app.domain.repository.SyncFailureClassifier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import org.koin.core.module.dsl.viewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.braincards.app.config.SUPABASE_ANON_KEY
import org.braincards.app.config.SUPABASE_URL
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.DataStoreAutoSyncPreference
import org.braincards.app.data.local.DataStoreLocalModePreference
import org.braincards.app.data.local.DataStorePullWatermarkStore
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
import org.braincards.app.data.repository.LocalDataRepositoryImpl
import org.braincards.app.data.repository.DeckSummariesObserverImpl
import org.braincards.app.data.repository.DueCardsObserverImpl
import org.braincards.app.data.repository.FreeReviewCardsSourceImpl
import org.braincards.app.data.repository.SampleDeckStoreImpl
import org.braincards.app.data.repository.StudyStatsObserverImpl
import org.braincards.app.data.repository.PendingChangesObserverImpl
import org.braincards.app.data.repository.ReviewLogRepositoryImpl
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.LocalDataRepository
import org.braincards.app.domain.repository.LocalTableInspector
import org.braincards.app.domain.repository.DeckSummariesObserver
import org.braincards.app.domain.repository.DueCardsObserver
import org.braincards.app.domain.repository.FreeReviewCardsSource
import org.braincards.app.domain.repository.StudyStatsObserver
import org.braincards.app.domain.repository.AutoSyncPreference
import org.braincards.app.domain.repository.LocalModePreference
import org.braincards.app.domain.repository.PendingChangesObserver
import org.braincards.app.domain.repository.PullWatermarkStore
import org.braincards.app.domain.repository.SampleDeckStore
import org.braincards.app.domain.repository.RemoteDataSource
import org.braincards.app.domain.repository.ReviewLogRepository
import org.braincards.app.domain.repository.TopicsRepository
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.ConfirmPasswordResetUseCase
import org.braincards.app.domain.usecase.Auth.DeleteAccountUseCase
import org.braincards.app.domain.usecase.Auth.SendPasswordResetUseCase
import org.braincards.app.domain.usecase.Auth.SignInUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.domain.usecase.Auth.SignOutUseCase
import org.braincards.app.domain.usecase.Auth.SignUpUseCase
import org.braincards.app.domain.usecase.Auth.UpdatePasswordUseCase
import org.braincards.app.domain.usecase.Auth.ResendSignUpCodeUseCase
import org.braincards.app.domain.usecase.Auth.VerifyPasswordResetCodeUseCase
import org.braincards.app.domain.usecase.Auth.VerifySignUpCodeUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import org.braincards.app.domain.usecase.Decks.AddDeckUseCase
import org.braincards.app.domain.usecase.Decks.CreateSampleDeckUseCase
import org.braincards.app.domain.usecase.Decks.GetDeckUseCase
import org.braincards.app.domain.usecase.Decks.ObserveDeckSummariesUseCase
import org.braincards.app.domain.usecase.Decks.UpdateDeckUseCase
import org.braincards.app.domain.usecase.DeleteDeckUseCase
import org.braincards.app.domain.usecase.Flashcards.AddFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.DeleteFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.GetDueFlashcardsUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardByIdUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.ObserveDueCountUseCase
import org.braincards.app.domain.usecase.Flashcards.ResolveStudyLabelUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFreeReviewCardsUseCase
import org.braincards.app.domain.usecase.Flashcards.ReviewFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.ScheduledRecorder
import org.braincards.app.domain.usecase.Flashcards.UpdateFlashcardUseCase
import org.braincards.app.domain.usecase.Stats.ObserveStudyStatsUseCase
import org.braincards.app.domain.usecase.Topics.AddTopicUseCase
import org.braincards.app.domain.usecase.Topics.DeleteTopicUseCase
import org.braincards.app.domain.usecase.Topics.GetTopicsUseCase
import org.braincards.app.domain.usecase.Topics.UpdateTopicUseCase
import org.braincards.app.presentation.screens.auth.AuthViewModel
import org.braincards.app.presentation.screens.auth.WelcomeViewModel
import org.braincards.app.presentation.screens.decks.DecksViewModel
import org.braincards.app.presentation.screens.flashcards.DeckContentViewModel
import org.braincards.app.presentation.screens.home.HomeViewModel
import org.braincards.app.presentation.screens.sample.SampleDeckViewModel
import org.braincards.app.presentation.screens.navigation.AppEntryViewModel
import org.braincards.app.presentation.screens.study.StudyViewModel
import org.braincards.app.presentation.screens.settings.AutoSyncSettingViewModel
import org.braincards.app.presentation.screens.settings.ThemeViewModel
import org.braincards.app.presentation.screens.studyStats.StudyStatsVM
import org.braincards.app.presentation.sync.AutoSyncPauseGate
import org.braincards.app.presentation.sync.SyncCoordinator
import org.braincards.app.presentation.sync.SyncStatusViewModel
import org.koin.dsl.binds
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.time.Duration.Companion.seconds

fun appModule(
    databaseBuilder: androidx.room.RoomDatabase.Builder<AppDatabase>,
    settingsDataStore: DataStore<Preferences>
) = module {

    // Sync automatica: el AutoSyncController (single, createdAtStart). Los monitores de plataforma entran por
    // initKoin(appDeclaration), ver AutoSyncModule.kt.
    includes(autoSyncModule())

    single<DataStore<Preferences>> { settingsDataStore }
    viewModel { ThemeViewModel(dataStore = get()) }
    viewModel { AutoSyncSettingViewModel(preference = get()) }

    // Cliente de Supabase (singleton). URL y clave vienen de `SupabaseConfig.kt`, generado en build/ a partir de local.properties
    // o variables de entorno: nunca van hardcodeadas.
    single<SupabaseClient> {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            // Explicito solo por documentacion -- supabase-kt 3.1.4 ya instala este
            // mismo default (10s) aunque no se declare (confirmado en el codigo
            // fuente real, KtorSupabaseHttpClient.kt/SupabaseClientBuilder.kt).
            requestTimeout = 10.seconds
            // Sin logs del cliente (el valor por defecto es INFO): una RestException o una traza de
            // peticion arrastra la URL y las cabeceras, Authorization incluido.
            defaultLogLevel = LogLevel.NONE
            // No cambiar enableLifecycleCallbacks ni alwaysAutoRefresh a la ligera. Con los valores por defecto supabase-kt resetea la
            // sesión a Initializing en onStop, pero desactivar los callbacks quita el refresco al volver tras un sueño largo (riesgo
            // de 401). Limitación conocida en Android: el último intento de sync en segundo plano no llega a sincronizar.
            install(Auth)
            install(Postgrest)
        }
    }
    single<AuthRepository> { AuthRepositoryImpl(client = get()) }

    // Envoltura fina sobre Postgrest; ver RemoteDataSourceImpl.
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
            flashcardRepository = get(),
            cardSchedulingRepository = get()
        )
    }
    // Clasifica los fallos remotos para el motor de sync (la implementación conoce Supabase/Ktor; el dominio no).
    single<SyncFailureClassifier> { SupabaseFailureClassifier }
    factory {
        SyncEngineUseCase(
            authRepository = get(),
            remoteDataSource = get(),
            decksRepository = get(),
            topicsRepository = get(),
            flashcardRepository = get(),
            cardSchedulingRepository = get(),
            backfillLocalDataUseCase = get(),
            failureClassifier = get(),
            watermarkStore = get(),
            localTables = get()
        )
    }
    factory {
        DeleteAccountUseCase(
            authRepository = get(),
            decksRepository = get(),
            topicsRepository = get(),
            flashcardRepository = get(),
            cardSchedulingRepository = get()
        )
    }

    // single(createdAtStart = true), no viewModel -- ver comentario de cabecera en
    // SyncCoordinator.kt sobre por que no puede ser un ViewModel de Koin aqui.
    // Reutiliza el DataStore<Preferences> ya registrado arriba (settingsDataStore),
    // no crea uno nuevo.
    // Puerta de pausa de la sync automatica, COMPARTIDA (coordinador, estudio): una sola instancia.
    single { AutoSyncPauseGate() }

    // Observa los cambios locales pendientes de subir (los 4 DAO): lo usa el AutoSyncController.
    single<PendingChangesObserver> {
        PendingChangesObserverImpl(
            deckDao = get(),
            topicDao = get(),
            flashcardDao = get(),
            cardSchedulingDao = get()
        )
    }

    single(createdAtStart = true) {
        SyncCoordinator(
            authRepository = get(),
            syncEngineUseCase = get(),
            decksRepository = get(),
            topicsRepository = get(),
            flashcardRepository = get(),
            cardSchedulingRepository = get(),
            dataStore = get(),
            localDataRepository = get(),
            signOutAndWipeUseCase = get(),
            pauseGate = get(),
            pendingObserver = get()
        )
    } onClose { it?.close() }

    viewModel {
        AuthViewModel(
            authRepository = get(),
            signUpUseCase = get(),
            signInUseCase = get(),
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
            // Necesario para que Room funcione con KMP.
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(kotlinx.coroutines.Dispatchers.IO)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }

    single { get<AppDatabase>().deckDao() }
    single { get<AppDatabase>().flashcardDao() }
    single {get<AppDatabase>().topicDao()}
    single {get<AppDatabase>().reviewLogDao()}
    single { get<AppDatabase>().cardSchedulingDao() }

    single<DecksRepository> { DeckRepositoryImpl(deckDao = get(), topicDao = get(), flashcardDao = get(), transactionRunner = get()) }
    single<FlashcardRepository> { FlashcardRepositoryImpl(flashcardDao = get()) }
    single<TopicsRepository> { TopicRepositoryImpl(topicDao = get(), flashcardDao = get(), transactionRunner = get()) }
    single<ReviewLogRepository> { ReviewLogRepositoryImpl(reviewLogDao = get()) }
    single<TransactionRunner> { RoomTransactionRunner(get<AppDatabase>()) }
    single<CardSchedulingRepository> { CardSchedulingRepositoryImpl(cardSchedulingDao = get(), transactionRunner = get()) }
    // UN solo singleton que responde a las dos interfaces (LocalDataRepositoryImpl implementa ambas): sin casts.
    single {
        LocalDataRepositoryImpl(
            deckDao = get(),
            topicDao = get(),
            flashcardDao = get(),
            cardSchedulingDao = get(),
            reviewLogDao = get(),
            transactionRunner = get()
        )
    } binds arrayOf(LocalDataRepository::class, LocalTableInspector::class)
    // Marca de agua del pull incremental sobre el DataStore compartido (clave propia, ver SyncPreferenceKeys).
    single<PullWatermarkStore> { DataStorePullWatermarkStore(dataStore = get()) }
    factory { SignOutAndWipeUseCase(authRepository = get(), localDataRepository = get()) }


    factory { AddDeckUseCase(repository = get()) }
    factory { GetDeckUseCase(repository = get()) }
    factory { UpdateDeckUseCase(repository = get()) }
    factory { DeleteDeckUseCase(repository = get()) }
    // Lista de mazos: mazos con su recuento de tarjetas (una consulta agregada), interfaz aditiva aparte.
    single<DeckSummariesObserver> { DeckSummariesObserverImpl(deckDao = get()) }
    factory { ObserveDeckSummariesUseCase(observer = get()) }
    // Mazo de ejemplo «Primeros pasos»: una sola transaccion con los repositorios de siempre (interfaz aditiva).
    single<SampleDeckStore> {
        SampleDeckStoreImpl(transactionRunner = get(), decksRepository = get(), topicsRepository = get(), flashcardRepository = get())
    }
    factory { CreateSampleDeckUseCase(decksRepository = get(), store = get()) }

    viewModel {
        DecksViewModel(
            addDeckUseCase = get(),
            observeDeckSummaries = get(),
            updateDeckUseCase = get(),
            deleteDeckUseCase = get()
        )
    }

    factory { AddFlashcardUseCase(repository = get()) }
    factory { GetFlashcardUseCase(repository = get()) }
    factory { UpdateFlashcardUseCase(repository = get()) }
    factory { DeleteFlashcardUseCase(repository = get()) }

    // Contenido de un mazo (temas + tarjetas + subnivel). El id del mazo llega como parametro de Koin.
    viewModel { (deckId: String) ->
        DeckContentViewModel(
            deckId = deckId,
            getDecks = get(),
            getTopics = get(),
            getFlashcards = get(),
            addTopicUseCase = get(),
            updateTopicUseCase = get(),
            deleteTopicUseCase = get(),
            addFlashcardUseCase = get(),
            updateFlashcardUseCase = get(),
            deleteFlashcardUseCase = get()
        )
    }

    // Home: contador reactivo de tarjetas pendientes (mismo criterio que el estudio) e indicador de sync.
    single<DueCardsObserver> { DueCardsObserverImpl(flashcardDao = get()) }
    factory { ObserveDueCountUseCase(observer = get()) }
    viewModel { HomeViewModel(observeDueCount = get(), observeDeckSummaries = get()) }
    viewModel { SampleDeckViewModel(createSampleDeck = get()) }
    viewModel { WelcomeViewModel(getDecks = get()) }

    // Arranque: flag de "modo local elegido" (DataStore) y ruta de entrada / sesion perdida.
    single<LocalModePreference> { DataStoreLocalModePreference(dataStore = get()) }
    // Interruptor de sincronizacion automatica (Ajustes). Lo lee el AutoSyncController.
    single<AutoSyncPreference> { DataStoreAutoSyncPreference(dataStore = get()) }
    viewModel {
        AppEntryViewModel(
            authRepository = get(),
            localModePreference = get(),
            userSessionExitCount = get<SyncCoordinator>().userSessionExitCount,
            userExitReason = get<SyncCoordinator>().userSessionExitReason,
            expectedUserExit = get<SyncCoordinator>().expectedUserExit
        )
    }
    viewModel {
        SyncStatusViewModel(
            authRepository = get(),
            syncCoordinator = get(),
            connectivityMonitor = get()
        )
    }

    factory { GetDueFlashcardsUseCase(repository = get()) }
    factory { ReviewFlashcardUseCase(reviewLogRepository = get(), cardSchedulingRepository = get()) }
    // Repaso libre: fuente de lectura aditiva, barajado con semilla y los dos recorders (el libre no persiste nada).
    single<FreeReviewCardsSource> { FreeReviewCardsSourceImpl(flashcardDao = get()) }
    factory { GetFreeReviewCardsUseCase(source = get()) }
    factory { ScheduledRecorder(reviewFlashcardUseCase = get()) }
    factory { GetFlashcardByIdUseCase(repository = get()) }
    // factory: una instancia por sesion de estudio, y su cache de nombres de mazo/tema vive lo que el VM.
    factory { ResolveStudyLabelUseCase(decksRepository = get(), topicsRepository = get()) }
    viewModel {
        StudyViewModel(
            getDueFlashcardsUseCase = get(),
            getFreeReviewCardsUseCase = get(),
            scheduledRecorder = get<ScheduledRecorder>(),
            getFlashcardByIdUseCase = get(),
            resolveStudyLabelUseCase = get(),
            syncPauseGate = get()
        )
    }

    factory { AddTopicUseCase(repository = get()) }
    factory { GetTopicsUseCase(repository = get()) }
    factory { UpdateTopicUseCase(repository = get()) }
    factory { DeleteTopicUseCase(repository = get()) }

    single<StudyStatsObserver> { StudyStatsObserverImpl(flashcardDao = get()) }
    factory { ObserveStudyStatsUseCase(observer = get()) }

    viewModel {
        StudyStatsVM(
            observeStudyStats = get(),
            observeDeckSummaries = get(),
            observeDueCount = get()
        )
    }


}