package org.braincards.app.domain.models

sealed class AuthState {
    data object Loading : AuthState()
    data object NotAuthenticated : AuthState()
    // sessionConfirmed sin valor por defecto a proposito: fuerza a decidir en cada
    // sitio que construye Authenticated si la sesion esta confirmada por el
    // servidor (true) o es la ultima guardada mientras se reintenta sin red
    // (false, ver AuthStateMapper.toAuthState) -- un default habria ocultado en
    // silencio los sitios que faltaba actualizar, igual que paso con los mappers.
    data class Authenticated(val userId: String, val email: String?, val sessionConfirmed: Boolean) : AuthState()
}
