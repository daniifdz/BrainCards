package org.braincards.app.presentation.screens.decks

import org.braincards.app.domain.usecase.Decks.DeckNameError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Error de campo del diálogo de mazo: «Escribe un nombre para el mazo.» solo cuando el campo está tocado (el valor cambió desde
 * que se abrió el diálogo) y en blanco. El error del VM (nombre repetido) manda sobre el de campo vacío.
 */
class DeckNameDialogErrorTest {

    @Test
    fun un_dialogo_recien_abierto_con_el_campo_vacio_no_muestra_error() {
        assertNull(shownDeckNameError(error = null, touched = false, name = ""))
    }

    @Test
    fun escribir_y_borrar_el_nombre_muestra_Blank() {
        assertEquals(DeckNameError.Blank, shownDeckNameError(error = null, touched = true, name = ""))
    }

    @Test
    fun un_nombre_de_solo_espacios_tocado_tambien_es_Blank() {
        assertEquals(DeckNameError.Blank, shownDeckNameError(error = null, touched = true, name = "   "))
    }

    @Test
    fun con_texto_no_hay_error_aunque_se_haya_tocado() {
        assertNull(shownDeckNameError(error = null, touched = true, name = "Inglés"))
    }

    @Test
    fun al_editar_un_mazo_con_nombre_sin_tocar_no_hay_error_y_al_borrarlo_todo_aparece() {
        assertNull(shownDeckNameError(error = null, touched = false, name = "Inglés B2"))
        assertEquals(DeckNameError.Blank, shownDeckNameError(error = null, touched = true, name = ""))
    }

    @Test
    fun el_error_del_VM_se_muestra_aunque_el_campo_no_este_tocado() {
        assertEquals(DeckNameError.Duplicate, shownDeckNameError(error = DeckNameError.Duplicate, touched = false, name = "Inglés"))
    }

    @Test
    fun el_error_del_VM_manda_sobre_el_de_campo_vacio() {
        assertEquals(DeckNameError.Duplicate, shownDeckNameError(error = DeckNameError.Duplicate, touched = true, name = ""))
    }
}
