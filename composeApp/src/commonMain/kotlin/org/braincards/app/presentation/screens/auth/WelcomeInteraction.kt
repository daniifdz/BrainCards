package org.braincards.app.presentation.screens.auth

/**
 * Eventos de la Bienvenida que pueden (o no) descartar el aviso persistente de sesion caducada. Solo los de la lista
 * de [isDismissingInteraction] se cablean en AuthScreen; los demas existen para dejar escrito, y probado, que NO lo
 * descartan (v4.9: "persiste hasta que el usuario interactua con el formulario").
 */
enum class WelcomeInteraction {
    // Interacciones con el formulario: descartan el aviso.
    EmailEdited,
    PasswordEdited,
    SubmitByButton,
    SubmitByIme,
    ToggleMode,
    ForgotPassword,
    ContinueWithoutAccount,
    Authenticated,

    // No son interaccion con el formulario: NO lo descartan.
    FocusGainedWithoutTyping,
    Scrolled,
    Rotated,
    ThemeChanged
}

/** `true` si [event] descarta el aviso persistente de sesion caducada. */
fun isDismissingInteraction(event: WelcomeInteraction): Boolean = when (event) {
    WelcomeInteraction.EmailEdited,
    WelcomeInteraction.PasswordEdited,
    WelcomeInteraction.SubmitByButton,
    WelcomeInteraction.SubmitByIme,
    WelcomeInteraction.ToggleMode,
    WelcomeInteraction.ForgotPassword,
    WelcomeInteraction.ContinueWithoutAccount,
    WelcomeInteraction.Authenticated -> true

    WelcomeInteraction.FocusGainedWithoutTyping,
    WelcomeInteraction.Scrolled,
    WelcomeInteraction.Rotated,
    WelcomeInteraction.ThemeChanged -> false
}
