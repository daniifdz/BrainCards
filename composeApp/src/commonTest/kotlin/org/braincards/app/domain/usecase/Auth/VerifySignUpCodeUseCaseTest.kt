package org.braincards.app.domain.usecase.Auth

import kotlinx.coroutines.runBlocking
import org.braincards.app.domain.fakes.FakeAuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerifySignUpCodeUseCaseTest {

    private val repository = FakeAuthRepository()
    private val useCase = VerifySignUpCodeUseCase(repository)

    @Test
    fun `email vacio lanza IllegalArgumentException`() = runBlocking {
        assertFailsWith<IllegalArgumentException> { useCase(email = "", code = "123456") }
        assertEquals(0, repository.verifySignUpCodeCallCount)
    }

    @Test
    fun `codigo con letras lanza IllegalArgumentException`() = runBlocking {
        assertFailsWith<IllegalArgumentException> { useCase(email = "a@b.com", code = "12a456") }
        assertEquals(0, repository.verifySignUpCodeCallCount)
    }

    @Test
    fun `codigo vacio tras quitar espacios lanza IllegalArgumentException`() = runBlocking {
        assertFailsWith<IllegalArgumentException> { useCase(email = "a@b.com", code = "   ") }
        assertEquals(0, repository.verifySignUpCodeCallCount)
    }

    @Test
    fun `codigo valido con espacios se limpia antes de llamar al repositorio`() = runBlocking {
        useCase(email = "a@b.com", code = "123 456")

        assertEquals(1, repository.verifySignUpCodeCallCount)
        assertEquals("123456", repository.lastVerifiedSignUpCode)
    }

    @Test
    fun `un fallo del repositorio se propaga`() = runBlocking {
        repository.verifySignUpCodeFailure = IllegalStateException("fallo simulado")

        assertFailsWith<IllegalStateException> { useCase(email = "a@b.com", code = "123456") }
        Unit
    }
}
