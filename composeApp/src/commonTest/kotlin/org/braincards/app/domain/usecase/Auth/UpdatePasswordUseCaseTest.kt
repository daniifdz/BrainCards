package org.braincards.app.domain.usecase.Auth

import kotlinx.coroutines.runBlocking
import org.braincards.app.domain.fakes.FakeAuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdatePasswordUseCaseTest {

    private val repository = FakeAuthRepository()
    private val useCase = UpdatePasswordUseCase(repository)

    @Test
    fun `contraseña vacia lanza IllegalArgumentException`() = runBlocking {
        assertFailsWith<IllegalArgumentException> { useCase(newPassword = "") }
        Unit
    }

    @Test
    fun `contraseña valida delega en el repositorio`() = runBlocking {
        useCase(newPassword = "unaContraseñaNueva")

        assertEquals(1, repository.updatePasswordCallCount)
    }
}
