package org.braincards.app.domain.usecase.Auth

import org.braincards.app.domain.repository.AuthRepository

class SignOutUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke() {
        repository.signOut()
    }
}
