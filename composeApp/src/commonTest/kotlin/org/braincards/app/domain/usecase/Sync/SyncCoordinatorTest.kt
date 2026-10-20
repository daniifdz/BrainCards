package org.braincards.app.domain.usecase.Sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
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
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.presentation.sync.SyncCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del coordinador unico de sync (Bloque D: sin conexion y reintentos).
 * coroutineScope = Dispatchers.Unconfined -- ejecuta las corrutinas del `init`
 * (observar authState) de forma deterministica dentro del mismo runBlocking, sin
 * necesidad de kotlinx-coroutines-test.
 */
class SyncCoordinatorTest {

    private val userId = "user-1"

    private fun buildDeck(id: String) = Deck(
        id = id,
        name = "Deck $id",
        createdAt = Instant.fromEpochMilliseconds(1000),
        updatedAt = Instant.fromEpochMilliseconds(2000),
        description = null,
        userId = userId,
        lastSyncedAt = null
    )

    private fun buildCoordinator(
        authRepository: FakeAuthRepository,
        remoteDataSource: FakeRemoteDataSource,
        decksRepository: FakeDecksRepository,
        topicsRepository: FakeTopicsRepository,
        flashcardRepository: FakeFlashcardRepository,
        cardSchedulingRepository: FakeCardSchedulingRepository,
        dataStore: FakeSettingsDataStore
    ): SyncCoordinator {
        val localDataRepository = FakeLocalDataRepository()
        val backfillLocalDataUseCase = BackfillLocalDataUseCase(decksRepository, topicsRepository, flashcardRepository, cardSchedulingRepository)
        val syncEngineUseCase = SyncEngineUseCase(
            authRepository = authRepository,
            remoteDataSource = remoteDataSource,
            decksRepository = decksRepository,
            topicsRepository = topicsRepository,
            flashcardRepository = flashcardRepository,
            cardSchedulingRepository = cardSchedulingRepository,
            backfillLocalDataUseCase = backfillLocalDataUseCase
        )
        return SyncCoordinator(
            authRepository = authRepository,
            syncEngineUseCase = syncEngineUseCase,
            decksRepository = decksRepository,
            topicsRepository = topicsRepository,
            flashcardRepository = flashcardRepository,
            cardSchedulingRepository = cardSchedulingRepository,
            dataStore = dataStore,
            localDataRepository = localDataRepository,
            signOutAndWipeUseCase = SignOutAndWipeUseCase(authRepository, localDataRepository),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun `no se lanzan dos syncs a la vez`() = runBlocking {
        val authRepository = FakeAuthRepository(initialState = AuthState.Authenticated(userId, "a@b.com", sessionConfirmed = true))
        val remoteDataSource = FakeRemoteDataSource().apply { pullDecksDelayMillis = 50 }
        val decksRepository = FakeDecksRepository()
        val topicsRepository = FakeTopicsRepository()
        val flashcardRepository = FakeFlashcardRepository()
        val dataStore = FakeSettingsDataStore()

        val coordinator = buildCoordinator(authRepository, remoteDataSource, decksRepository, topicsRepository, flashcardRepository, FakeCardSchedulingRepository(), dataStore)

        // El init ya dispara un sync automatico al construirse (Authenticated
        // confirmado) -- lanzamos ademas 2 manuales concurrentes, para estresar el
        // Mutex con 3 intentos solapables en total.
        listOf(
            async { coordinator.syncNow() },
            async { coordinator.syncNow() }
        ).awaitAll()

        assertEquals(1, remoteDataSource.maxConcurrentPullDecksCalls)
    }

    @Test
    fun `sin sesion activa no sincroniza`() = runBlocking {
        val authRepository = FakeAuthRepository(initialState = AuthState.NotAuthenticated)
        val remoteDataSource = FakeRemoteDataSource()
        val decksRepository = FakeDecksRepository()
        val topicsRepository = FakeTopicsRepository()
        val flashcardRepository = FakeFlashcardRepository()
        val dataStore = FakeSettingsDataStore()

        val coordinator = buildCoordinator(authRepository, remoteDataSource, decksRepository, topicsRepository, flashcardRepository, FakeCardSchedulingRepository(), dataStore)

        assertTrue(remoteDataSource.calls.isEmpty())
        assertEquals(0, coordinator.pendingChangesCount.value)
        assertNull(coordinator.lastSuccessfulSyncAt.value)
    }

    @Test
    fun `un fallo de red deja pendientes y el siguiente sync los sube`() = runBlocking {
        val authRepository = FakeAuthRepository(initialState = AuthState.Authenticated(userId, "a@b.com", sessionConfirmed = true))
        val remoteDataSource = FakeRemoteDataSource().apply {
            pushDeckIdsThatFail = setOf("deck-fails")
        }
        val decksRepository = FakeDecksRepository()
        val topicsRepository = FakeTopicsRepository()
        val flashcardRepository = FakeFlashcardRepository()
        val dataStore = FakeSettingsDataStore()
        decksRepository.addDeck(buildDeck("deck-fails"))

        // El sync automatico del init ya corre con el push fallando -- deja la fila
        // pendiente y no persiste lastSuccessfulSyncAt.
        val coordinator = buildCoordinator(authRepository, remoteDataSource, decksRepository, topicsRepository, flashcardRepository, FakeCardSchedulingRepository(), dataStore)

        assertTrue(coordinator.pendingChangesCount.value > 0)
        assertNull(coordinator.lastSuccessfulSyncAt.value)

        // Se arregla la red y se reintenta en el siguiente sync.
        remoteDataSource.pushDeckIdsThatFail = emptySet()
        coordinator.syncNow()

        assertEquals(0, coordinator.pendingChangesCount.value)
        assertNotNull(coordinator.lastSuccessfulSyncAt.value)
        Unit
    }

    @Test
    fun `el estado persiste la ultima sincronizacion solo si fue correcta`() = runBlocking {
        val authRepository = FakeAuthRepository(initialState = AuthState.Authenticated(userId, "a@b.com", sessionConfirmed = true))
        val remoteDataSource = FakeRemoteDataSource().apply {
            pushDeckIdsThatFail = setOf("deck-fails")
        }
        val decksRepository = FakeDecksRepository()
        val topicsRepository = FakeTopicsRepository()
        val flashcardRepository = FakeFlashcardRepository()
        val dataStore = FakeSettingsDataStore()
        decksRepository.addDeck(buildDeck("deck-fails"))

        // Primer sync (automatico del init) falla parcialmente -- no se persiste nada.
        val firstCoordinator = buildCoordinator(authRepository, remoteDataSource, decksRepository, topicsRepository, flashcardRepository, FakeCardSchedulingRepository(), dataStore)
        assertNull(firstCoordinator.lastSuccessfulSyncAt.value)

        // Se arregla la red y se reintenta -- ahora si tiene exito completo.
        remoteDataSource.pushDeckIdsThatFail = emptySet()
        firstCoordinator.syncNow()
        assertNotNull(firstCoordinator.lastSuccessfulSyncAt.value)

        // Un SEGUNDO coordinador sobre la MISMA FakeSettingsDataStore, sin sesion
        // (para que no corra ningun sync propio) simula "reabrir la app": si de
        // verdad se persistio (no solo en memoria del StateFlow del primero), su
        // propio init debe leerlo del DataStore compartido.
        val secondCoordinator = buildCoordinator(
            FakeAuthRepository(initialState = AuthState.NotAuthenticated),
            FakeRemoteDataSource(),
            FakeDecksRepository(),
            FakeTopicsRepository(),
            FakeFlashcardRepository(),
            FakeCardSchedulingRepository(),
            dataStore
        )
        assertNotNull(secondCoordinator.lastSuccessfulSyncAt.value)
        Unit
    }

    @Test
    fun `el contador de pendientes incluye el scheduling que no se pudo subir`() = runBlocking {
        val authRepository = FakeAuthRepository(initialState = AuthState.Authenticated(userId, "a@b.com", sessionConfirmed = true))
        val remoteDataSource = FakeRemoteDataSource().apply {
            pushCardSchedulingIdsThatFail = setOf("card-1")
        }
        val cardSchedulingRepository = FakeCardSchedulingRepository()
        cardSchedulingRepository.upsert(
            CardScheduling(
                cardId = "card-1", easeFactor = 2.5, interval = 1, repetitions = 1,
                nextReview = Instant.fromEpochMilliseconds(5000), updatedAt = Instant.fromEpochMilliseconds(2000),
                userId = userId, lastSyncedAt = null
            )
        )

        // Solo hay scheduling pendiente (ningun deck/topic/flashcard): si el contador no lo
        // sumara, valdria 0. El sync automatico del init falla en su push y lo deja pendiente.
        val coordinator = buildCoordinator(
            authRepository, remoteDataSource, FakeDecksRepository(), FakeTopicsRepository(),
            FakeFlashcardRepository(), cardSchedulingRepository, FakeSettingsDataStore()
        )

        assertEquals(1, coordinator.pendingChangesCount.value)

        remoteDataSource.pushCardSchedulingIdsThatFail = emptySet()
        coordinator.syncNow()

        assertEquals(0, coordinator.pendingChangesCount.value)
        Unit
    }
}
