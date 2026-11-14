package org.braincards.app.presentation.screens.navigation

import kotlinx.serialization.Serializable
import org.braincards.app.domain.models.StudyScope

@Serializable
sealed class Screen {
    @Serializable
    data object Home : Screen()
    @Serializable
    data object DecksList : Screen()
    @Serializable
    data class FlashcardsList(val deckId: String) : Screen()
    @Serializable
    data class StudySession(val deckId: String? = null) : Screen()
    @Serializable

    data object Settings : Screen()
    @Serializable

    data object Stats : Screen()
    @Serializable
    // asEntry: true si es la Bienvenida de ENTRADA de la app (sin flecha; «Continuar sin cuenta» va a Home);
    // false si se apilo desde Home o Ajustes (con flecha; «Continuar sin cuenta» vuelve al origen).
    data class Auth(val asEntry: Boolean = false) : Screen()
    @Serializable
    // email: el que se habia escrito en Bienvenida, para precargarlo ("" = sin precarga).
    data class ForgotPassword(val email: String = "") : Screen()
    @Serializable
    data class ResetPassword(val email: String) : Screen()
    @Serializable
    // codeJustSent: true si se llega justo tras registrarse (el email de confirmacion
    // acaba de enviarse, asi que el boton "Reenviar" arranca con cooldown); false si se
    // llega desde un login con cuenta sin confirmar (no se ha enviado nada).
    data class ConfirmEmail(val email: String, val codeJustSent: Boolean = false) : Screen()
}