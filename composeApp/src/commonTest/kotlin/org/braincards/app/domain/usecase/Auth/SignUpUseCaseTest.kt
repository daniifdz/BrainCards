package org.braincards.app.domain.usecase.Auth

import kotlinx.coroutines.runBlocking
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.models.SignUpOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SignUpUseCaseTest {

    private val repository = FakeAuthRepository()
    private val useCase = SignUpUseCase(repository)

    @Test
    fun `email vacio lanza IllegalArgumentException`() = runBlocking {
        assertFailsWith<IllegalArgumentException> { useCase(email = "", password = "secreto123") }
        assertEquals(0, repository.signUpCallCount)
    }

    @Test
    fun `contraseña vacia lanza IllegalArgumentException`() = runBlocking {
        assertFailsWith<IllegalArgumentException> { useCase(email = "a@b.com", password = "") }
        assertEquals(0, repository.signUpCallCount)
    }

    @Test
    fun `devuelve ConfirmationRequired cuando el servidor no da sesion`() = runBlocking {
        repository.signUpOutcome = SignUpOutcome.ConfirmationRequired

        assertEquals(SignUpOutcome.ConfirmationRequired, useCase("a@b.com", "secreto123"))
        assertEquals("a@b.com", repository.lastSignUpEmail)
    }

    @Test
    fun `devuelve SessionStarted cuando el servidor da sesion`() = runBlocking {
        repository.signUpOutcome = SignUpOutcome.SessionStarted

        assertEquals(SignUpOutcome.SessionStarted, useCase("a@b.com", "secreto123"))
    }
}
