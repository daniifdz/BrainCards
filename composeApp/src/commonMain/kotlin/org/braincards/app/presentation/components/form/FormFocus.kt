package org.braincards.app.presentation.components.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Orden de foco EXPLICITO de un formulario: un [FocusRequester] por campo de texto, en orden. Sirve para pedir el
 * foco desde codigo (p. ej. el primer campo con error tras un intento de envio) y para encadenar Tab / Enter.
 */
class FormFocus internal constructor(private val requesters: List<FocusRequester>) {
    val size: Int get() = requesters.size

    internal fun requester(index: Int): FocusRequester = requesters[index]

    /** Pone el foco en el campo [index] (0 = primero). Ignora indices fuera de rango. */
    fun requestFocus(index: Int) {
        // Un campo deshabilitado o aun sin componer no puede tomar el foco: se ignora.
        runCatching { requesters.getOrNull(index)?.requestFocus() }
    }
}

@Composable
fun rememberFormFocus(count: Int): FormFocus =
    remember(count) { FormFocus(List(count) { FocusRequester() }) }

/** Foco inicial en el primer campo: se espera un fotograma porque dentro de un dialogo el campo aun no esta colocado. */
@Composable
fun InitialFocus(focus: FormFocus) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestFocus(0)
    }
}

/** Foco inicial en un [FocusRequester] suelto (p. ej. "Cancelar" en los dialogos de eliminar). */
@Composable
fun InitialFocus(requester: FocusRequester) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

/**
 * Marca un campo de texto como el [index]-esimo del formulario. La decision de teclado vive en [formKeyAction]:
 *  - Campo de una linea ([multiline] = false): Tab / Shift+Tab siguen el orden explicito (focusProperties); Enter pasa
 *    al campo siguiente o, en el ULTIMO, ejecuta [onSubmit].
 *  - Campo multilinea ([multiline] = true): Tab / Shift+Tab mueven el foco; Enter inserta salto de linea;
 *    Ctrl/Cmd+Enter ejecuta [onSubmit] desde cualquier campo.
 * Los eventos tratados se consumen, asi no se dispara tambien la accion del teclado en pantalla (IME) del campo.
 *
 * Solo encadena campos de texto: el boton de mostrar contrasena y los de enviar siguen el orden natural del arbol.
 */
fun Modifier.formField(
    focus: FormFocus,
    index: Int,
    multiline: Boolean = false,
    onSubmit: () -> Unit
): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val last = focus.size - 1
    val next = if (index < last) focus.requester(index + 1) else null
    val previous = if (index > 0) focus.requester(index - 1) else null
    this
        .focusRequester(focus.requester(index))
        .focusProperties {
            if (next != null) this.next = next
            if (previous != null) this.previous = previous
        }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown && event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
            val action = formKeyAction(
                key = event.key,
                ctrl = event.isCtrlPressed,
                meta = event.isMetaPressed,
                shift = event.isShiftPressed,
                alt = event.isAltPressed,
                multiline = multiline,
                isLast = index == last
            )
            if (!action.consumesEvent) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyUp) return@onPreviewKeyEvent true // ya tratado en KeyDown
            when (action) {
                FormKeyAction.FocusNext ->
                    if (multiline || next == null) focusManager.moveFocus(FocusDirection.Next) else next.requestFocus()
                FormKeyAction.FocusPrevious -> focusManager.moveFocus(FocusDirection.Previous)
                FormKeyAction.Submit -> onSubmit()
                else -> Unit
            }
            true
        }
}
