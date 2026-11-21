package org.braincards.app.presentation.screens.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {
    @Serializable
    data object Home : Screen()
    @Serializable
    data object DecksList : Screen()
    @Serializable
    // sampleCreated: se llega desde «Crear mazo de ejemplo»; el contenido muestra una vez el aviso «Mazo de ejemplo creado».
    data class FlashcardsList(val deckId: String, val sampleCreated: Boolean = false) : Screen()
    @Serializable
    // freeReview / seed (repaso libre): por defecto es la sesión normal. La semilla del orden aleatorio viaja aquí para que un
    // reinicio del proceso reproduzca el mismo orden; la genera el punto de navegación (Random.nextLong()).
    data class StudySession(val deckId: String? = null, val freeReview: Boolean = false, val seed: Long = 0L) : Screen()
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