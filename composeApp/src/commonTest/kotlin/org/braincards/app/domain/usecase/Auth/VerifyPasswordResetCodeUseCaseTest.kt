package org.braincards.app.domain.usecase.Auth

import kotlinx.coroutines.runBlocking
import org.braincards.app.domain.fakes.FakeAuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerifyPasswordResetCodeUseCaseTest {

    private val repository = FakeAuthRepository()
    private val useCase = VerifyPasswordResetCodeUseCase(repository)

    @Test
    fun `email vacio lanza IllegalArgumentException`() = runBlocking {
        assertFailsWith<IllegalArgumentException> { useCase(email = "", code = "123456") }
        Unit
    }

    @Test
    fun `codigo con letras lanza IllegalArgumentException`() = runBlocking {
        assertFailsWith<IllegalArgumentException> { useCase(email = "a@b.com", code = "12a456") }
        Unit
    }

    @Test
    fun `codigo vacio tras quitar espacios lanza IllegalArgumentException`() = runBlocking {
        assertFailsWith<IllegalArgumentException> { useCase(email = "a@b.com", code = "   ") }
        Unit
    }

    @Test
    fun `codigo valido con espacios se limpia antes de llamar al repositorio`() = runBlocking {
        useCase(email = "a@b.com", code = "123 456")

        assertEquals(1, repository.verifyPasswordResetCodeCallCount)
        assertEquals("123456", repository.lastVerifiedCode)
    }
}
