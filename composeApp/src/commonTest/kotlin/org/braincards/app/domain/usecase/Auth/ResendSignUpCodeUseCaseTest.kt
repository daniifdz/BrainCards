package org.braincards.app.domain.usecase.Auth

import kotlinx.coroutines.runBlocking
import org.braincards.app.domain.fakes.FakeAuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResendSignUpCodeUseCaseTest {

    private val repository = FakeAuthRepository()
    private val useCase = ResendSignUpCodeUseCase(repository)

    @Test
    fun `email vacio lanza IllegalArgumentException`() = runBlocking {
        assertFailsWith<IllegalArgumentException> { useCase(email = " ") }
        assertEquals(0, repository.resendSignUpCodeCallCount)
    }

    @Test
    fun `email valido delega en el repositorio`() = runBlocking {
        useCase(email = "a@b.com")

        assertEquals(1, repository.resendSignUpCodeCallCount)
        assertEquals("a@b.com", repository.lastResentEmail)
    }
}
