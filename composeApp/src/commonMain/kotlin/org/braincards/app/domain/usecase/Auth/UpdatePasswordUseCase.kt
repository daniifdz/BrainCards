package org.braincards.app.domain.usecase.Auth

import org.braincards.app.domain.repository.AuthRepository

class UpdatePasswordUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(newPassword: String) {
        require(newPassword.isNotBlank()) { "La contraseña no puede estar vacía" }
        repository.updatePassword(newPassword)
    }
}
