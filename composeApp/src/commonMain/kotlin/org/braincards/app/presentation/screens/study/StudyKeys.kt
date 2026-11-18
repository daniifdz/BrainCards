package org.braincards.app.presentation.screens.study

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import org.braincards.app.domain.models.ReviewQuality

/** Que hace la sesion de estudio ante una tecla (Desktop). Ver [studyKeyAction]. */
sealed interface StudyKeyAction {
    /** Mostrar la respuesta. */
    data object Reveal : StudyKeyAction

    /** Puntuar la tarjeta con [quality]. */
    data class Rate(val quality: ReviewQuality) : StudyKeyAction

    /** Salir de la sesion (mismo destino que el ✕). */
    data object Exit : StudyKeyAction

    /** No hacer nada: el evento NO se consume y sigue su camino (p. ej. activa un boton con foco). */
    data object Ignore : StudyKeyAction
}

/**
 * Decision pura de teclado de la sesion de estudio (handoff 5.5, confirmado):
 *  - Pregunta en pantalla ([answerVisible] = false): Espacio o Enter (tambien el del teclado numerico) -> [StudyKeyAction.Reveal].
 *  - Respuesta visible: 1 · 2 · 3 · 4 (tambien NumPad) -> Rate(Otra vez / Dificil / Bien / Facil). Espacio y Enter se
 *    IGNORAN (y el llamador NO los consume): un boton con foco por Tab debe seguir activandose, y un Espacio por reflejo no
 *    debe puntuar nada.
 *  - Esc -> [StudyKeyAction.Exit] en cualquier estado.
 *  - Cualquier combinacion con Ctrl, Alt o Meta se ignora (tambien Esc): son atajos del sistema o de la ventana.
 */
fun studyKeyAction(key: Key, ctrl: Boolean, alt: Boolean, meta: Boolean, answerVisible: Boolean): StudyKeyAction {
    if (ctrl || alt || meta) return StudyKeyAction.Ignore
    return when (key) {
        Key.Escape -> StudyKeyAction.Exit
        Key.Spacebar, Key.Enter, Key.NumPadEnter ->
            if (answerVisible) StudyKeyAction.Ignore else StudyKeyAction.Reveal
        Key.One, Key.NumPad1 -> rate(answerVisible, ReviewQuality.AGAIN)
        Key.Two, Key.NumPad2 -> rate(answerVisible, ReviewQuality.HARD)
        Key.Three, Key.NumPad3 -> rate(answerVisible, ReviewQuality.GOOD)
        Key.Four, Key.NumPad4 -> rate(answerVisible, ReviewQuality.EASY)
        else -> StudyKeyAction.Ignore
    }
}

private fun rate(answerVisible: Boolean, quality: ReviewQuality): StudyKeyAction =
    if (answerVisible) StudyKeyAction.Rate(quality) else StudyKeyAction.Ignore

/**
 * Sin tarjeta en pantalla (cargando, error, vacio, fin) solo vale Esc: Espacio/Enter no se roban a los botones de esos
 * estados (p. ej. "Volver al inicio" con foco).
 */
fun gateStudyKeyAction(action: StudyKeyAction, cardShown: Boolean): StudyKeyAction =
    if (cardShown || action == StudyKeyAction.Exit) action else StudyKeyAction.Ignore

/**
 * Teclado de la sesion sobre el CONTENEDOR (no sobre ningun boton): Espacio/Enter/1-4/Esc segun [studyKeyAction]. Los
 * eventos tratados se consumen (KeyDown y KeyUp); [StudyKeyAction.Ignore] devuelve false y el evento sigue su camino.
 */
fun Modifier.studyKeys(
    cardShown: Boolean,
    answerVisible: Boolean,
    onReveal: () -> Unit,
    onRate: (ReviewQuality) -> Unit,
    onExit: () -> Unit
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown && event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
    val action = gateStudyKeyAction(
        studyKeyAction(event.key, event.isCtrlPressed, event.isAltPressed, event.isMetaPressed, answerVisible),
        cardShown
    )
    if (action == StudyKeyAction.Ignore) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyDown) dispatchStudyKeyAction(action, onReveal, onRate, onExit)
    true
}

/** Ejecuta la accion de teclado ya decidida. Separado de [studyKeys] para poder probar que las teclas 1-4 llegan a [onRate]. */
fun dispatchStudyKeyAction(
    action: StudyKeyAction,
    onReveal: () -> Unit,
    onRate: (ReviewQuality) -> Unit,
    onExit: () -> Unit
) {
    when (action) {
        StudyKeyAction.Reveal -> onReveal()
        is StudyKeyAction.Rate -> onRate(action.quality)
        StudyKeyAction.Exit -> onExit()
        StudyKeyAction.Ignore -> Unit
    }
}
