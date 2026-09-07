package org.braincards.app.domain.usecase.Auth

import org.braincards.app.domain.repository.AuthRepository

class SignUpUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String) {
        require(email.isNotBlank()) { "El email no puede estar vacío" }
        require(password.isNotBlank()) { "La contraseña no puede estar vacía" }
        repository.signUp(email, password)
    }
}
