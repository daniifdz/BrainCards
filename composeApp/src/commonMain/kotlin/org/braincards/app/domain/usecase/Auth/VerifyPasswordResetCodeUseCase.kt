package org.braincards.app.domain.usecase.Auth

import org.braincards.app.domain.repository.AuthRepository

class VerifyPasswordResetCodeUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, code: String) {
        require(email.isNotBlank()) { "El email no puede estar vacío" }
        val cleanCode = code.replace(" ", "")
        require(cleanCode.isNotBlank() && cleanCode.all { it.isDigit() }) {
            "El código debe contener solo dígitos"
        }
        repository.verifyPasswordResetCode(email, cleanCode)
    }
}
