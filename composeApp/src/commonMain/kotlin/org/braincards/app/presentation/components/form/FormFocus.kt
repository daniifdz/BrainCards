package org.braincards.app.presentation.components.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

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

/**
 * Marca un campo de texto como el [index]-esimo del formulario:
 *  - Tab / Shift+Tab (Desktop) van al campo siguiente / anterior del orden explicito.
 *  - Enter (teclado fisico, Desktop): en un campo intermedio pasa al siguiente; en el ULTIMO ejecuta [onSubmit].
 *    Se consume el evento, asi que no se dispara tambien la accion del teclado en pantalla (IME) del campo.
 *
 * Solo encadena campos de texto: el boton de mostrar contrasena y el de enviar siguen el orden natural del arbol.
 */
fun Modifier.formField(focus: FormFocus, index: Int, onSubmit: () -> Unit): Modifier {
    val last = focus.size - 1
    val next = if (index < last) focus.requester(index + 1) else null
    val previous = if (index > 0) focus.requester(index - 1) else null
    return this
        .focusRequester(focus.requester(index))
        .focusProperties {
            if (next != null) this.next = next
            if (previous != null) this.previous = previous
        }
        .onPreviewKeyEvent { event ->
            val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
            if (!isEnter) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyUp) return@onPreviewKeyEvent true // ya tratado en KeyDown
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (next != null) next.requestFocus() else onSubmit()
            true
        }
}
