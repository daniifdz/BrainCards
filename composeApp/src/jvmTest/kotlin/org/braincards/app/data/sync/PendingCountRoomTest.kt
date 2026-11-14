package org.braincards.app.data.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.repository.CardSchedulingRepositoryImpl
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.FlashcardRepositoryImpl
import org.braincards.app.data.repository.LocalDataRepositoryImpl
import org.braincards.app.data.repository.PendingChangesObserverImpl
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import org.braincards.app.presentation.sync.SyncCoordinator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contador de pendientes de la UI contra una Room REAL: una tarjeta creada sin conexion (userId NULL, aun sin
 * adoptar) sube el contador sin ningun sync. Confirma tambien que el recuento antiguo (getPendingSync) NO veia
 * esas filas, que era la causa de que el contador se quedase en 0.
 */
class PendingCountRoomTest {

    private val userId = "user-1"
    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var remote: FakeRemoteDataSource
    private lateinit var decks: DeckRepositoryImpl
    private lateinit var flashcards: FlashcardRepositoryImpl
    private lateinit var coordinator: SyncCoordinator

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        remote = FakeRemoteDataSource()
        decks = DeckRepositoryImpl(db.deckDao(), db.topicDao(), db.flashcardDao(), RoomTransactionRunner(db))
        val topics = TopicRepositoryImpl(db.topicDao(), db.flashcardDao())
        flashcards = FlashcardRepositoryImpl(db.flashcardDao())
        val scheduling = CardSchedulingRepositoryImpl(db.cardSchedulingDao(), RoomTransactionRunner(db))
        val local = LocalDataRepositoryImpl(
            db.deckDao(), db.topicDao(), db.flashcardDao(), db.cardSchedulingDao(), db.reviewLogDao(), RoomTransactionRunner(db)
        )
        val auth = FakeAuthRepository(AuthState.Authenticated(userId, "a@b.c", sessionConfirmed = true))
        coordinator = SyncCoordinator(
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
            pendingObserver = PendingChangesObserverImpl(db.deckDao(), db.topicDao(), db.flashcardDao(), db.cardSchedulingDao())
        )
    }

    @AfterTest
    fun tearDown() {
        coordinator.close()
        scope.cancel()
        db.close()
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }

    @Test
    fun una_tarjeta_creada_sin_dueno_sube_el_contador_sin_sync_y_el_recuento_antiguo_no_la_veia() = runBlocking<Unit> {
        waitUntil { coordinator.pendingChangesCount.value == 0 }

        // Como al crear una tarjeta sin conexion: filas sin dueno (userId NULL), sin subir.
        db.deckDao().addDeck(
            DeckEntity(id = "d1", name = "d", createdAt = ms(1), updatedAt = ms(100), description = null,
                lastSyncedAt = null, userId = null)
        )
        db.flashcardDao().addFlashcard(
            FlashcardEntity(id = "c1", deckId = "d1", topicId = null, question = "Q", answer = "A",
                cardType = CardType.FLIP, createdAt = ms(1), updatedAt = ms(100), lastSyncedAt = null, userId = null)
        )

        waitUntil { coordinator.pendingChangesCount.value == 2 }
        assertTrue(remote.calls.isEmpty(), "sin ningun sync")
        // La inferencia: el recuento anterior (getPendingSync) no ve filas sin dueno, por eso se quedaba en 0.
        assertEquals(0, decks.getPendingSync(userId).size + flashcards.getPendingSync(userId).size)
    }

    @Test
    fun una_fila_propia_pendiente_y_su_subida_mueven_el_contador() = runBlocking<Unit> {
        db.deckDao().addDeck(
            DeckEntity(id = "d1", name = "d", createdAt = ms(1), updatedAt = ms(100), description = null,
                lastSyncedAt = null, userId = userId)
        )
        waitUntil { coordinator.pendingChangesCount.value == 1 }

        db.deckDao().markDecksPushed(listOf("d1"), ms(200))

        waitUntil { coordinator.pendingChangesCount.value == 0 }
    }
}
