package org.braincards.app.presentation.components.form

import androidx.compose.ui.input.key.Key

/** Que hace un campo de formulario ante una tecla (Desktop). Ver [formKeyAction]. */
enum class FormKeyAction {
    /** Mover el foco al siguiente elemento (campo siguiente o, tras el ultimo, el orden natural: selector, Cancelar...). */
    FocusNext,

    FocusPrevious,

    /** Enviar el formulario (equivale al boton primario). */
    Submit,

    /** Dejar que el campo inserte el salto de linea (no se consume el evento). */
    InsertNewline,

    /** No hacer nada: el evento sigue su camino normal. */
    Ignore;

    /** `true` si el evento se consume (el campo no lo trata como texto). */
    val consumesEvent: Boolean get() = this == FocusNext || this == FocusPrevious || this == Submit
}

/**
 * Decisión pura de teclado de un campo de formulario.
 *
 * - Campo de una línea ([multiline] = false): Enter, con cualquier modificador, pasa al campo siguiente o, en el [isLast],
 *   envía. Tab se deja al foco nativo ([Ignore]).
 * - Campo multilínea: Tab / Shift+Tab mueven el foco (el campo no inserta tabulador); Enter inserta salto de línea;
 *   Ctrl+Enter o Cmd(Meta)+Enter envía desde cualquier campo. Tab con Ctrl/Alt/Meta y Enter con Shift/Alt se ignoran/insertan.
 *
 * [isLast] = es el último campo de texto del orden. Solo afecta al Enter de una línea; el destino de FocusNext tras el último
 * campo lo decide el orden natural del árbol (así un selector nuevo entra sin tocar esto).
 */
fun formKeyAction(
    key: Key,
    ctrl: Boolean,
    meta: Boolean,
    shift: Boolean,
    alt: Boolean,
    multiline: Boolean,
    isLast: Boolean
): FormKeyAction {
    val isEnter = key == Key.Enter || key == Key.NumPadEnter
    val isTab = key == Key.Tab
    return when {
        isEnter && !multiline -> if (isLast) FormKeyAction.Submit else FormKeyAction.FocusNext
        isEnter && (ctrl || meta) -> FormKeyAction.Submit
        isEnter -> FormKeyAction.InsertNewline
        isTab && multiline && !ctrl && !meta && !alt ->
            if (shift) FormKeyAction.FocusPrevious else FormKeyAction.FocusNext
        else -> FormKeyAction.Ignore
    }
}
