package org.braincards.app.domain.usecase.Auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.Deck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeleteAccountUseCaseTest {

    private val userId = "user-1"

    private fun buildDeck(id: String) = Deck(
        id = id,
        name = "Deck $id",
        createdAt = Instant.fromEpochMilliseconds(1000),
        updatedAt = Instant.fromEpochMilliseconds(2000),
        description = null,
        userId = userId,
        lastSyncedAt = Instant.fromEpochMilliseconds(2000)
    )

    private fun buildUseCase(
        authRepository: FakeAuthRepository,
        decksRepository: FakeDecksRepository,
        topicsRepository: FakeTopicsRepository,
        flashcardRepository: FakeFlashcardRepository,
        cardSchedulingRepository: FakeCardSchedulingRepository = FakeCardSchedulingRepository()
    ) = DeleteAccountUseCase(
        authRepository = authRepository,
        decksRepository = decksRepository,
        topicsRepository = topicsRepository,
        flashcardRepository = flashcardRepository,
        cardSchedulingRepository = cardSchedulingRepository
    )

    @Test
    fun `borrado exitoso invoca el RPC, cierra sesion y libera las filas locales`() = runBlocking {
        val authRepository = FakeAuthRepository(
            initialState = AuthState.Authenticated(userId = userId, email = "a@b.com", sessionConfirmed = true)
        )
        val decksRepository = FakeDecksRepository()
        val topicsRepository = FakeTopicsRepository()
        val flashcardRepository = FakeFlashcardRepository()
        decksRepository.addDeck(buildDeck("deck-1"))

        val useCase = buildUseCase(authRepository, decksRepository, topicsRepository, flashcardRepository)

        assertEquals(DeleteAccountResult.Deleted, useCase(userId))

        assertTrue(authRepository.deleteAccountCalled)
        assertTrue(authRepository.signOutCalled)
        assertEquals(AuthState.NotAuthenticated, authRepository.authState.first())
        assertEquals(null, decksRepository.getDeckById("deck-1")?.userId)
    }

    @Test
    fun `si el RPC falla no se toca la sesion ni las filas locales`() = runBlocking {
        val authRepository = FakeAuthRepository(
            initialState = AuthState.Authenticated(userId = userId, email = "a@b.com", sessionConfirmed = true)
        ).apply {
            deleteAccountFailure = RuntimeException("fallo de red simulado")
        }
        val decksRepository = FakeDecksRepository()
        val topicsRepository = FakeTopicsRepository()
        val flashcardRepository = FakeFlashcardRepository()
        decksRepository.addDeck(buildDeck("deck-1"))

        val useCase = buildUseCase(authRepository, decksRepository, topicsRepository, flashcardRepository)

        assertFailsWith<RuntimeException> { useCase(userId) }

        assertTrue(authRepository.deleteAccountCalled)
        assertTrue(!authRepository.signOutCalled)
        assertEquals(
            AuthState.Authenticated(userId = userId, email = "a@b.com", sessionConfirmed = true),
            authRepository.authState.first()
        )
        assertEquals(userId, decksRepository.getDeckById("deck-1")?.userId)
    }

    @Test
    fun `si el RPC tiene exito pero signOut falla, se cierra la sesion local y las filas quedan liberadas`() = runBlocking {
        val authRepository = FakeAuthRepository(
            initialState = AuthState.Authenticated(userId = userId, email = "a@b.com", sessionConfirmed = true)
        ).apply {
            signOutFailure = RuntimeException("fallo al cerrar sesion local simulado")
        }
        val decksRepository = FakeDecksRepository()
        val topicsRepository = FakeTopicsRepository()
        val flashcardRepository = FakeFlashcardRepository()
        decksRepository.addDeck(buildDeck("deck-1"))

        val useCase = buildUseCase(authRepository, decksRepository, topicsRepository, flashcardRepository)

        // No debe lanzar: el borrado remoto ya ocurrio, un signOut() local que falla
        // no es un fallo del borrado de cuenta. El respaldo cierra la sesion sin red.
        assertEquals(DeleteAccountResult.Deleted, useCase(userId))

        assertTrue(authRepository.deleteAccountCalled)
        assertTrue(authRepository.signOutCalled)
        assertTrue(authRepository.clearLocalSessionCalled)
        assertEquals(AuthState.NotAuthenticated, authRepository.authState.first())
        assertEquals(null, decksRepository.getDeckById("deck-1")?.userId)
    }

    @Test
    fun `el respaldo de sesion solo limpia la sesion local y los mazos locales siguen ahi`() = runBlocking {
        val callLog = mutableListOf<String>()
        val authRepository = FakeAuthRepository(
            initialState = AuthState.Authenticated(userId = userId, email = "a@b.com", sessionConfirmed = true),
            callLog = callLog
        ).apply {
            signOutFailure = RuntimeException("sin red")
        }
        val decksRepository = FakeDecksRepository()
        decksRepository.addDeck(buildDeck("deck-1"))
        decksRepository.addDeck(buildDeck("deck-2"))

        val useCase = buildUseCase(authRepository, decksRepository, FakeTopicsRepository(), FakeFlashcardRepository())

        assertEquals(DeleteAccountResult.Deleted, useCase(userId))

        // Orden: primero signOut (falla), luego SOLO clearLocalSession; el use case ni siquiera tiene un
        // LocalDataRepository, asi que no puede llamar a wipeAll. Los mazos siguen ahi, sin dueno.
        assertEquals(listOf("signOut", "clearLocalSession"), callLog)
        assertEquals(null, decksRepository.getDeckById("deck-1")?.userId)
        assertEquals("Deck deck-1", decksRepository.getDeckById("deck-1")?.name)
        assertEquals("Deck deck-2", decksRepository.getDeckById("deck-2")?.name)
    }

    @Test
    fun `si fallan signOut y clearLocalSession devuelve DeletedSessionNotClosed y las filas quedan liberadas`() = runBlocking {
        val authRepository = FakeAuthRepository(
            initialState = AuthState.Authenticated(userId = userId, email = "a@b.com", sessionConfirmed = true)
        ).apply {
            signOutFailure = RuntimeException("sin red")
            clearLocalSessionFailure = RuntimeException("disco")
        }
        val decksRepository = FakeDecksRepository()
        decksRepository.addDeck(buildDeck("deck-1"))

        val useCase = buildUseCase(authRepository, decksRepository, FakeTopicsRepository(), FakeFlashcardRepository())

        assertEquals(DeleteAccountResult.DeletedSessionNotClosed, useCase(userId))

        assertEquals(null, decksRepository.getDeckById("deck-1")?.userId)
        assertEquals(
            AuthState.Authenticated(userId = userId, email = "a@b.com", sessionConfirmed = true),
            authRepository.authState.first()
        )
    }
}
