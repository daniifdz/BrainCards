package org.braincards.app.domain.usecase.Auth

import kotlinx.coroutines.runBlocking
import org.braincards.app.domain.fakes.FakeAuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfirmPasswordResetUseCaseTest {

    private val repository = FakeAuthRepository()
    private val verifyPasswordResetCodeUseCase = VerifyPasswordResetCodeUseCase(repository)
    private val updatePasswordUseCase = UpdatePasswordUseCase(repository)
    private val useCase = ConfirmPasswordResetUseCase(verifyPasswordResetCodeUseCase, updatePasswordUseCase)

    @Test
    fun `exito en un intento llama verify y updatePassword una vez cada uno`() = runBlocking {
        useCase(email = "a@b.com", code = "123456", newPassword = "nueva123")

        assertEquals(1, repository.verifyPasswordResetCodeCallCount)
        assertEquals(1, repository.updatePasswordCallCount)
        assertEquals(false, useCase.isCodeVerified("a@b.com"))
    }

    @Test
    fun `verify exitoso con updatePassword fallido - el reintento no repite verify`() = runBlocking {
        repository.updatePasswordFailure = RuntimeException("fallo simulado")

        assertFailsWith<RuntimeException> {
            useCase(email = "a@b.com", code = "123456", newPassword = "nueva123")
        }
        assertEquals(1, repository.verifyPasswordResetCodeCallCount)
        assertEquals(1, repository.updatePasswordCallCount)
        assertEquals(true, useCase.isCodeVerified("a@b.com"))

        repository.updatePasswordFailure = null
        useCase(email = "a@b.com", code = "999999", newPassword = "nueva123")

        // verify sigue en 1 -- el reintento no lo repite porque el codigo ya estaba verificado.
        assertEquals(1, repository.verifyPasswordResetCodeCallCount)
        assertEquals(2, repository.updatePasswordCallCount)
    }

    @Test
    fun `onNewCodeRequested invalida la verificacion previa`() = runBlocking {
        repository.updatePasswordFailure = RuntimeException("fallo simulado")
        assertFailsWith<RuntimeException> {
            useCase(email = "a@b.com", code = "123456", newPassword = "nueva123")
        }
        assertEquals(true, useCase.isCodeVerified("a@b.com"))

        useCase.onNewCodeRequested()
        assertEquals(false, useCase.isCodeVerified("a@b.com"))

        repository.updatePasswordFailure = null
        useCase(email = "a@b.com", code = "111111", newPassword = "nueva123")

        // verify se vuelve a llamar porque el codigo anterior ya no cuenta como verificado.
        assertEquals(2, repository.verifyPasswordResetCodeCallCount)
    }
}
