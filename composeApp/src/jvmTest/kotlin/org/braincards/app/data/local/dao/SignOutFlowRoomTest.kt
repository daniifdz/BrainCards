package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.repository.CardSchedulingRepositoryImpl
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.FlashcardRepositoryImpl
import org.braincards.app.data.repository.LocalDataRepositoryImpl
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeResult
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import org.braincards.app.presentation.sync.FinishSignOutResult
import org.braincards.app.presentation.sync.SignOutPrompt
import org.braincards.app.presentation.sync.SyncCoordinator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Cierre de sesion de punta a punta: SyncCoordinator + SyncEngine + repositorios Room REALES
 * (en memoria) con auth y remoto falsos. Sin retrasos de ningun tipo: el hueco entre el borrado
 * y clearSession se mantiene abierto con una compuerta (CompletableDeferred).
 */
class SignOutFlowRoomTest {

    private val userA = "user-a"
    private val userB = "user-b"
    private val tables = listOf("decks", "topics", "flashcards", "card_scheduling", "reviewlog")

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var auth: FakeAuthRepository
    private lateinit var remote: FakeRemoteDataSource
    private lateinit var coordinator: SyncCoordinator

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    private fun remoteDeck(id: String, owner: String) = Deck(
        id = id, name = id, createdAt = ms(1000), updatedAt = ms(2000), description = null, userId = owner
    )

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        auth = FakeAuthRepository(initialState = AuthState.NotAuthenticated)
        remote = FakeRemoteDataSource()

        val transactionRunner = RoomTransactionRunner(db)
        val decks = DeckRepositoryImpl(deckDao = db.deckDao(), topicDao = db.topicDao(), flashcardDao = db.flashcardDao(), transactionRunner = RoomTransactionRunner(db))
        val topics = TopicRepositoryImpl(topicDao = db.topicDao(), flashcardDao = db.flashcardDao())
        val flashcards = FlashcardRepositoryImpl(flashcardDao = db.flashcardDao())
        val scheduling = CardSchedulingRepositoryImpl(cardSchedulingDao = db.cardSchedulingDao(), transactionRunner = transactionRunner)
        val localData = LocalDataRepositoryImpl(
            deckDao = db.deckDao(), topicDao = db.topicDao(), flashcardDao = db.flashcardDao(),
            cardSchedulingDao = db.cardSchedulingDao(), reviewLogDao = db.reviewLogDao(),
            transactionRunner = transactionRunner
        )
        coordinator = SyncCoordinator(
            authRepository = auth,
            syncEngineUseCase = SyncEngineUseCase(
                authRepository = auth,
                remoteDataSource = remote,
                decksRepository = decks,
                topicsRepository = topics,
                flashcardRepository = flashcards,
                cardSchedulingRepository = scheduling,
                backfillLocalDataUseCase = BackfillLocalDataUseCase(decks, topics, flashcards, scheduling)
            ),
            decksRepository = decks,
            topicsRepository = topics,
            flashcardRepository = flashcards,
            cardSchedulingRepository = scheduling,
            dataStore = FakeSettingsDataStore(),
            localDataRepository = localData,
            signOutAndWipeUseCase = SignOutAndWipeUseCase(auth, localData),
            coroutineScope = scope
        )
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private suspend fun count(table: String): Long = db.useWriterConnection { transactor ->
        transactor.usePrepared("SELECT COUNT(*) FROM \"$table\"") { stmt ->
            stmt.step()
            stmt.getLong(0)
        }
    }

    private suspend fun rowCounts(): Map<String, Long> = tables.associateWith { count(it) }

    /** Inicia sesion como [userId] y sincroniza: la BD local queda con lo que haya en el remoto. */
    private suspend fun signInAndSync(userId: String) {
        auth.setState(AuthState.Authenticated(userId = userId, email = "$userId@x.com", sessionConfirmed = true))
        coordinator.syncNow()
    }

    // --- el hueco entre el borrado y clearSession ---

    @Test
    fun un_sync_lanzado_en_el_hueco_entre_el_borrado_y_el_cierre_de_sesion_no_repuebla_la_bd() = runBlocking {
        remote.decksToPull = listOf(remoteDeck("deck-a", userA))
        signInAndSync(userA)
        assertEquals(1L, count("decks"), "precondicion: la BD tiene el mazo de A")

        val gate = CompletableDeferred<Unit>()
        auth.signOutGate = gate
        val finish = async { coordinator.finishSignOut(userA, confirmedCount = 0) }
        auth.signOutEntered.await() // el borrado ya ocurrio y el signOut remoto esta parado en la compuerta

        // Hueco: BD vacia pero sesion todavia viva (el sync, si pudiera, vería a A autenticado).
        assertEquals(0L, count("decks"))
        assertEquals(AuthState.Authenticated(userA, "$userA@x.com", true), auth.authState.first())

        val pullsBefore = remote.calls.count { it == "pullDecks" }
        val syncInGap = async { coordinator.syncNow() }
        repeat(20) { yield() }

        // Compuerta cerrada: el sync NO avanza (espera al mutex que tiene finishSignOut).
        assertFalse(syncInGap.isCompleted, "el sync del hueco no debe terminar mientras la compuerta esta cerrada")
        assertEquals(pullsBefore, remote.calls.count { it == "pullDecks" })

        gate.complete(Unit)
        val result = finish.await()
        syncInGap.await()

        assertEquals(FinishSignOutResult.Finished(SignOutAndWipeResult.SignedOut), result)
        // El sync del hueco se ejecuto DESPUES del cierre, vio NotAuthenticated y no bajo nada.
        assertEquals(tables.associateWith { 0L }, rowCounts())
        assertEquals(pullsBefore, remote.calls.count { it == "pullDecks" })

        // Y un sync posterior tampoco repuebla nada de la cuenta anterior.
        coordinator.syncNow()
        assertEquals(tables.associateWith { 0L }, rowCounts())
    }

    // --- cancelar no limpia ---

    @Test
    fun si_el_usuario_cancela_el_dialogo_no_se_borra_ninguna_fila_ni_se_cierra_la_sesion() = runBlocking {
        remote.pushDeckIdsThatFail = setOf("deck-a")
        db.deckDao().addDeck(
            org.braincards.app.data.local.entity.DeckEntity(
                id = "deck-a", name = "A", createdAt = ms(1000), updatedAt = ms(2000), description = null, userId = userA
            )
        )
        signInAndSync(userA)
        val before = rowCounts()
        assertEquals(1L, before.getValue("decks"))

        val prompt = coordinator.prepareSignOut()
        assertEquals(SignOutPrompt.NeedsConfirmation(1), prompt)
        // El usuario pulsa "Cancelar": no hay finishSignOut.

        assertEquals(before, rowCounts())
        assertFalse(auth.signOutCalled)
        assertFalse(auth.clearLocalSessionCalled)
        assertEquals(AuthState.Authenticated(userA, "$userA@x.com", true), auth.authState.first())
    }

    // --- confirmar vacia todo; el cambio de cuenta no ve datos de la anterior ---

    @Test
    fun tras_cerrar_sesion_y_entrar_con_otra_cuenta_no_se_ve_nada_de_la_anterior() = runBlocking {
        remote.decksToPull = listOf(remoteDeck("deck-a", userA))
        signInAndSync(userA)
        assertEquals(1L, count("decks"))

        val result = coordinator.finishSignOut(userA, confirmedCount = 0)

        assertEquals(FinishSignOutResult.Finished(SignOutAndWipeResult.SignedOut), result)
        assertEquals(tables.associateWith { 0L }, rowCounts())
        assertEquals(AuthState.NotAuthenticated, auth.authState.first())

        // Cuenta B en el mismo dispositivo: el remoto de B solo tiene su mazo.
        remote.decksToPull = listOf(remoteDeck("deck-b", userB))
        signInAndSync(userB)

        assertEquals(1L, count("decks"))
        assertNull(db.deckDao().getDeckById("deck-a"), "el mazo de A no debe verse con la sesion de B")
        assertEquals("deck-b", db.deckDao().getDeckById("deck-b")?.id)
    }
}
