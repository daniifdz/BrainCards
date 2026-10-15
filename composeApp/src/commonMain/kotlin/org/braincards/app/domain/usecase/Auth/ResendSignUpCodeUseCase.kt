package org.braincards.app.domain.usecase.Auth

import org.braincards.app.domain.repository.AuthRepository

class ResendSignUpCodeUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String) {
        require(email.isNotBlank()) { "El email no puede estar vacío" }
        repository.resendSignUpCode(email)
    }
}
