package org.braincards.app.domain.usecase.Sync

import org.braincards.app.data.remote.SupabaseFailureClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeLocalDataRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.PullWatermarks
import org.braincards.app.domain.models.RemoteRow
import org.braincards.app.domain.models.SyncTable
import org.braincards.app.domain.repository.PullWatermarkStore
import org.braincards.app.domain.repository.RemoteDataSource
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.presentation.sync.SyncCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** SyncCoordinator.requestFullPull(): pull completo forzado, con el mismo Mutex que cualquier sync. */
class SyncCoordinatorFullPullTest {

    private val userId = "user-1"

    /** Almacen en memoria con una marca reciente (cursor de decks + fecha de completo de hace 1 hora). */
    private class SeededStore(private val owner: String) : PullWatermarkStore {
        val cursor = PullCursor(Instant.parse("2025-03-01T10:00:00Z"), "d1")
        private val marks = PullWatermarks(
            cursors = mapOf(SyncTable.DECKS to cursor),
            lastFullPullAt = Clock.System.now() - 1.hours
        )

        override suspend fun load(userId: String): PullWatermarks? = marks.takeIf { owner == userId }
        override suspend fun saveCursor(userId: String, table: SyncTable, cursor: PullCursor) = Unit
        override suspend fun saveFullPullAt(userId: String, at: Instant) = Unit
    }

    /** Anota con que marca se pide el pull de decks (y delega en el fake). */
    private class DecksWatermarkRecorder(private val delegate: FakeRemoteDataSource) : RemoteDataSource by delegate {
        val watermarks = mutableListOf<PullCursor?>()

        override suspend fun pullDecksSince(userId: String, watermark: PullCursor?): List<RemoteRow<Deck>> {
            watermarks.add(watermark)
            return delegate.pullDecksSince(userId, watermark)
        }
    }

    private class Fixture(userId: String, authenticated: Boolean = true, pullDelayMillis: Long = 0) {
        val auth = FakeAuthRepository(
            if (authenticated) AuthState.Authenticated(userId, "a@b.com", sessionConfirmed = true) else AuthState.NotAuthenticated
        )
        val fakeRemote = FakeRemoteDataSource().apply { pullDecksDelayMillis = pullDelayMillis }
        val recorder = DecksWatermarkRecorder(fakeRemote)
        val store = SeededStore(userId)
        val decks = FakeDecksRepository()
        val topics = FakeTopicsRepository()
        val flashcards = FakeFlashcardRepository()
        val scheduling = FakeCardSchedulingRepository()
        val localData = FakeLocalDataRepository()
        val coordinator = SyncCoordinator(
            authRepository = auth,
            syncEngineUseCase = SyncEngineUseCase(
                authRepository = auth,
                remoteDataSource = recorder,
                decksRepository = decks,
                topicsRepository = topics,
                flashcardRepository = flashcards,
                cardSchedulingRepository = scheduling,
                backfillLocalDataUseCase = BackfillLocalDataUseCase(decks, topics, flashcards, scheduling),
                watermarkStore = store,
                failureClassifier = SupabaseFailureClassifier
            ),
            decksRepository = decks,
            topicsRepository = topics,
            flashcardRepository = flashcards,
            cardSchedulingRepository = scheduling,
            dataStore = FakeSettingsDataStore(),
            localDataRepository = localData,
            signOutAndWipeUseCase = SignOutAndWipeUseCase(auth, localData),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun `un sync normal usa la marca guardada y requestFullPull la ignora`() = runBlocking<Unit> {
        val f = Fixture(userId)

        f.coordinator.syncNow()
        assertEquals(f.store.cursor, f.recorder.watermarks.last(), "sync normal: incremental con la marca")

        val result = f.coordinator.requestFullPull()

        assertNull(f.recorder.watermarks.last(), "requestFullPull: pull completo (sin marca)")
        val completed = assertIs<SyncResult.Completed>(result)
        assertTrue(completed.pull.decks.fullPull && completed.pull.topics.fullPull)
        assertTrue(completed.pull.flashcards.fullPull && completed.pull.cardScheduling.fullPull)
    }

    @Test
    fun `requestFullPull sin sesion devuelve NotAuthenticated y no toca el remoto`() = runBlocking<Unit> {
        val f = Fixture(userId, authenticated = false)

        val result = f.coordinator.requestFullPull()

        assertEquals(SyncResult.NotAuthenticated, result)
        assertTrue(f.fakeRemote.calls.isEmpty())
    }

    @Test
    fun `requestFullPull usa el mismo mutex que el resto de syncs`() = runBlocking<Unit> {
        val f = Fixture(userId, pullDelayMillis = 50)

        listOf(
            async { f.coordinator.syncNow() },
            async { f.coordinator.requestFullPull() },
            async { f.coordinator.syncNow() }
        ).awaitAll()

        assertEquals(1, f.fakeRemote.maxConcurrentPullDecksCalls)
    }

    @Test
    fun `tras requestFullPull el estado de ultima sincronizacion se actualiza como en cualquier sync`() = runBlocking<Unit> {
        val f = Fixture(userId)

        f.coordinator.requestFullPull()

        assertNotNull(f.coordinator.lastSuccessfulSyncAt.value)
    }
}
