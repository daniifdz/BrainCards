package org.braincards.app.platform

/** Aspecto de la barra de estado que pide la app. */
sealed interface StatusBarMode {
    /** Iconos segun el tema EFECTIVO de la app ([dark] = tema oscuro, iconos claros). */
    data class FollowTheme(val dark: Boolean) : StatusBarMode

    /** Iconos CLAROS fijos: la barra esta sobre el morado de EntryPending (fijo en claro y oscuro). */
    data object LightIcons : StatusBarMode
}

/**
 * Decision pura del aspecto de la barra de estado (Android). Mientras EntryPending esta visible, su fondo es el morado fijo
 * del splash: los iconos son claros aunque el tema efectivo sea claro. El flag se baja al EMPEZAR el fundido de salida.
 */
fun statusBarMode(appDark: Boolean, entryPendingVisible: Boolean): StatusBarMode =
    if (entryPendingVisible) StatusBarMode.LightIcons else StatusBarMode.FollowTheme(appDark)
