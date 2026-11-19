package org.braincards.app.presentation.components.form

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.braincards.app.presentation.components.form.FormKeyAction.FocusNext
import org.braincards.app.presentation.components.form.FormKeyAction.FocusPrevious
import org.braincards.app.presentation.components.form.FormKeyAction.Ignore
import org.braincards.app.presentation.components.form.FormKeyAction.InsertNewline
import org.braincards.app.presentation.components.form.FormKeyAction.Submit

/** Teclado de formularios y diálogos: campos de una línea frente a multilínea. */
class DialogKeyActionTest {

    private data class Case(
        val name: String,
        val key: Key,
        val multiline: Boolean,
        val isLast: Boolean,
        val expected: FormKeyAction,
        val ctrl: Boolean = false,
        val meta: Boolean = false,
        val shift: Boolean = false,
        val alt: Boolean = false
    )

    private fun run(c: Case) = formKeyAction(c.key, c.ctrl, c.meta, c.shift, c.alt, c.multiline, c.isLast)

    @Test
    fun tabla_de_teclado() {
        listOf(
            Case("Tab multilinea -> siguiente", Key.Tab, true, false, FocusNext),
            Case("Tab en el ultimo multilinea -> siguiente (orden natural)", Key.Tab, true, true, FocusNext),
            Case("Shift+Tab multilinea -> anterior", Key.Tab, true, false, FocusPrevious, shift = true),
            Case("Ctrl+Tab multilinea -> ignora", Key.Tab, true, false, Ignore, ctrl = true),
            Case("Alt+Tab multilinea -> ignora", Key.Tab, true, false, Ignore, alt = true),
            Case("Enter multilinea -> salto de linea", Key.Enter, true, false, InsertNewline),
            Case("Enter multilinea en el ultimo -> salto de linea", Key.Enter, true, true, InsertNewline),
            Case("NumPadEnter multilinea -> salto de linea", Key.NumPadEnter, true, true, InsertNewline),
            Case("Shift+Enter multilinea -> salto de linea", Key.Enter, true, false, InsertNewline, shift = true),
            Case("Ctrl+Enter multilinea (primero) -> envia", Key.Enter, true, false, Submit, ctrl = true),
            Case("Ctrl+Enter multilinea (ultimo) -> envia", Key.Enter, true, true, Submit, ctrl = true),
            Case("Cmd+Enter (Meta) multilinea -> envia", Key.Enter, true, false, Submit, meta = true),
            Case("Ctrl+NumPadEnter multilinea -> envia", Key.NumPadEnter, true, false, Submit, ctrl = true),
            Case("otra tecla multilinea -> ignora", Key.A, true, false, Ignore),
            Case("Escape multilinea -> ignora (lo cierra el Dialog)", Key.Escape, true, false, Ignore),
            // Una linea (comportamiento previo de Auth / mazo / tema)
            Case("Enter una linea intermedio -> siguiente", Key.Enter, false, false, FocusNext),
            Case("Enter una linea ultimo -> envia", Key.Enter, false, true, Submit),
            Case("NumPadEnter una linea ultimo -> envia", Key.NumPadEnter, false, true, Submit),
            Case("Ctrl+Enter una linea ultimo -> envia (como antes)", Key.Enter, false, true, Submit, ctrl = true),
            Case("Shift+Enter una linea intermedio -> siguiente (como antes)", Key.Enter, false, false, FocusNext, shift = true),
            Case("Tab una linea -> ignora (foco nativo)", Key.Tab, false, false, Ignore),
            Case("Shift+Tab una linea -> ignora (foco nativo)", Key.Tab, false, false, Ignore, shift = true),
            Case("otra tecla una linea -> ignora", Key.A, false, true, Ignore)
        ).forEach { assertEquals(it.expected, run(it), it.name) }
    }

    @Test
    fun meta_cuenta_igual_que_ctrl() {
        for (key in listOf(Key.Enter, Key.NumPadEnter)) for (last in listOf(false, true)) {
            assertEquals(
                formKeyAction(key, true, false, false, false, true, last),
                formKeyAction(key, false, true, false, false, true, last)
            )
        }
    }

    @Test
    fun solo_se_consume_lo_que_se_trata() {
        assertTrue(FocusNext.consumesEvent)
        assertTrue(FocusPrevious.consumesEvent)
        assertTrue(Submit.consumesEvent)
        assertFalse(InsertNewline.consumesEvent) // el campo debe insertar el salto
        assertFalse(Ignore.consumesEvent)
    }
}
