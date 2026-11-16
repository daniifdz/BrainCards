package org.braincards.app.presentation.components.form

/** Medidas del simbolo de BrainCards (icono + wordmark) en la Bienvenida. Valores en dp / sp. */
data class WelcomeLogoSpec(
    val iconDp: Float,
    val wordmarkSp: Float,
    val gapDp: Float
)

/** Por debajo de este ancho de ventana el simbolo usa el tamano compacto (handoff v4.10). */
const val WELCOME_COMPACT_WIDTH_DP = 360f

/** Por debajo de este alto de ventana el simbolo usa el tamano compacto (handoff v4.10). */
const val WELCOME_COMPACT_HEIGHT_DP = 600f

/**
 * Tamano del simbolo de la Bienvenida (handoff v4.10): dos tamanos fijos por tipo de dispositivo, sin interpolar.
 *
 *  - Compacto (ancho < 360 dp o alto < 600 dp): icono 44 / wordmark 27 (Desktop/tablet) o 40 / 25 (movil), gap 10.
 *  - Normal: icono 88 / wordmark 54 / gap 14 (Desktop/tablet) o 80 / 50 / 12 (movil).
 *
 * Funcion pura. [widthDp] y [heightDp] deben ser el tamano de la ventana SIN descontar el teclado (IME).
 */
fun welcomeLogoSpec(widthDp: Float, heightDp: Float, isMobile: Boolean): WelcomeLogoSpec {
    val compact = widthDp < WELCOME_COMPACT_WIDTH_DP || heightDp < WELCOME_COMPACT_HEIGHT_DP
    return when {
        compact && isMobile -> WelcomeLogoSpec(iconDp = 40f, wordmarkSp = 25f, gapDp = 10f)
        compact -> WelcomeLogoSpec(iconDp = 44f, wordmarkSp = 27f, gapDp = 10f)
        isMobile -> WelcomeLogoSpec(iconDp = 80f, wordmarkSp = 50f, gapDp = 12f)
        else -> WelcomeLogoSpec(iconDp = 88f, wordmarkSp = 54f, gapDp = 14f)
    }
}
