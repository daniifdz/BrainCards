package org.braincards.app.presentation.screens.flashcards

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "touched" = el usuario cambio el valor del campo desde que se abrio el dialogo (no el foco). */
class BlankFieldErrorTest {

    @Test
    fun un_dialogo_recien_abierto_con_el_campo_vacio_no_muestra_error() {
        assertFalse(shouldShowBlankError(touched = false, value = ""))
    }

    @Test
    fun editar_un_nombre_y_dejarlo_vacio_muestra_error() {
        assertTrue(shouldShowBlankError(touched = true, value = ""))
        assertTrue(shouldShowBlankError(touched = true, value = "   "))
    }

    @Test
    fun con_texto_no_hay_error_aunque_se_haya_tocado() {
        assertFalse(shouldShowBlankError(touched = true, value = "Verbs"))
    }

    @Test
    fun editar_un_dialogo_con_texto_sin_tocar_no_muestra_error() {
        assertFalse(shouldShowBlankError(touched = false, value = "Phrasal verbs"))
        // Un valor inicial en blanco (no deberia pasar al editar) tampoco muestra error hasta que se toque.
        assertFalse(shouldShowBlankError(touched = false, value = "  "))
    }
}
