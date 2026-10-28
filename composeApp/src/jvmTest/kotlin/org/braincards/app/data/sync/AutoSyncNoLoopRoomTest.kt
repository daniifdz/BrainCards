package org.braincards.app.data.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.repository.CardSchedulingRepositoryImpl
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.FlashcardRepositoryImpl
import org.braincards.app.data.repository.LocalDataRepositoryImpl
import org.braincards.app.data.repository.PendingChangesObserverImpl
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import org.braincards.app.presentation.sync.AutoSyncConfig
import org.braincards.app.presentation.sync.AutoSyncController
import org.braincards.app.presentation.sync.AutoSyncPauseGate
import org.braincards.app.presentation.sync.ConnectivityMonitor
import org.braincards.app.presentation.sync.ForegroundMonitor
import org.braincards.app.presentation.sync.SyncCoordinator
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Sin bucles, de punta a punta y en TIEMPO REAL (plazos cortos): Room real + PendingChangesObserverImpl +
 * SyncCoordinator + AutoSyncController. Un cambio local da UN sync; el push (markPushed) y el pull (que aplica filas)
 * no provocan otro.
 */
class AutoSyncNoLoopRoomTest {

    private val userId = "user-1"
    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var remote: FakeRemoteDataSource
    private lateinit var decks: DeckRepositoryImpl
    private lateinit var controller: AutoSyncController

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        remote = FakeRemoteDataSource()
        decks = DeckRepositoryImpl(db.deckDao(), db.topicDao(), db.flashcardDao())
        val topics = TopicRepositoryImpl(db.topicDao(), db.flashcardDao())
        val flashcards = FlashcardRepositoryImpl(db.flashcardDao())
        val scheduling = CardSchedulingRepositoryImpl(db.cardSchedulingDao(), RoomTransactionRunner(db))
        val local = LocalDataRepositoryImpl(
            db.deckDao(), db.topicDao(), db.flashcardDao(), db.cardSchedulingDao(), db.reviewLogDao(), RoomTransactionRunner(db)
        )
        val auth = FakeAuthRepository(AuthState.Authenticated(userId, "a@b.c", sessionConfirmed = true))
        val gate = AutoSyncPauseGate()
        val coordinator = SyncCoordinator(
            authRepository = auth,
            syncEngineUseCase = SyncEngineUseCase(
                auth, remote, decks, topics, flashcards, scheduling,
                BackfillLocalDataUseCase(decks, topics, flashcards, scheduling)
            ),
            decksRepository = decks,
            topicsRepository = topics,
            flashcardRepository = flashcards,
            cardSchedulingRepository = scheduling,
            dataStore = FakeSettingsDataStore(),
            localDataRepository = local,
            signOutAndWipeUseCase = SignOutAndWipeUseCase(auth, local),
            coroutineScope = scope,
            pauseGate = gate
        )
        controller = AutoSyncController(
            scope = scope,
            timeSource = TimeSource.Monotonic,
            random = Random(1),
            host = coordinator,
            authRepository = auth,
            pendingObserver = PendingChangesObserverImpl(db.deckDao(), db.topicDao(), db.flashcardDao(), db.cardSchedulingDao()),
            pauseGate = gate,
            foregroundMonitor = object : ForegroundMonitor { override val isForeground = MutableStateFlow(true) },
            connectivityMonitor = object : ConnectivityMonitor { override val isOnline = MutableStateFlow(true) },
            config = AutoSyncConfig(
                debounce = 100.milliseconds, maxChangeWait = 500.milliseconds, minInterval = 200.milliseconds,
                periodicInterval = 1.hours, backgroundTimeout = 1_000.milliseconds,
                backoffBase = 100.milliseconds, pausedRetry = 100.milliseconds
            )
        )
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private val pulls get() = remote.calls.count { it == "pullDecks" }
    private val pushes get() = remote.pushedDeckIds.size

    private fun deck(id: String, updatedAt: Instant) =
        Deck(id = id, name = id, createdAt = updatedAt, updatedAt = updatedAt, description = null, userId = userId)

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(20) }
    }

    @Test
    fun un_cambio_local_da_un_solo_sync_y_ni_el_push_ni_el_pull_provocan_otro() = runBlocking<Unit> {
        controller.start()
        delay(800) // sync de arranque (init del coordinador + el del controlador, unidos por el Mutex)
        val pullsBefore = pulls

        decks.addDeck(deck("d1", Clock.System.now()))
        waitUntil { pushes == 1 }
        delay(1_500) // el markPushed y la lectura del pull invalidan las tablas: no debe haber otro sync

        assertEquals(1, pushes, "la fila se sube una sola vez")
        assertEquals(pullsBefore + 1, pulls, "un solo sync por el cambio, ninguno mas")
    }

    @Test
    fun un_pull_que_aplica_filas_no_dispara_otro_sync() = runBlocking<Unit> {
        remote.decksToPull = listOf(deck("remoto", Clock.System.now()).copy(lastSyncedAt = null))
        controller.start()
        delay(800)
        val pullsBefore = pulls
        assertEquals(true, db.deckDao().getDeckById("remoto") != null, "el pull aplico la fila")

        delay(1_500)

        assertEquals(pullsBefore, pulls, "aplicar filas del servidor no crea pendientes ni otro sync")
    }
}
