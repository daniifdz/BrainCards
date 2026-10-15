package org.braincards.app.domain.usecase.Auth

import org.braincards.app.domain.models.SignUpOutcome
import org.braincards.app.domain.repository.AuthRepository

class SignUpUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): SignUpOutcome {
        require(email.isNotBlank()) { "El email no puede estar vacío" }
        require(password.isNotBlank()) { "La contraseña no puede estar vacía" }
        return repository.signUpWithConfirmation(email, password)
    }
}
