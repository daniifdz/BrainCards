package org.braincards.app.domain.models

sealed class AuthState {
    data object Loading : AuthState()
    data object NotAuthenticated : AuthState()
    data class Authenticated(val userId: String, val email: String?) : AuthState()
}
