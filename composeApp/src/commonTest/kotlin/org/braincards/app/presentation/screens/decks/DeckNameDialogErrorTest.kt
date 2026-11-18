package org.braincards.app.presentation.screens.decks

import org.braincards.app.domain.usecase.Decks.DeckNameError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Error de campo del dialogo de mazo (handoff v4.19/v4.20, `lista-mazos-dialogos.html`): «Escribe un nombre para el
 * mazo.» solo cuando el campo esta TOCADO (el valor cambio desde que se abrio el dialogo) y en blanco. El error del VM
 * (nombre repetido) manda sobre el de campo vacio.
 */
class DeckNameDialogErrorTest {

    // Mutacion N1: si se ignorara `touched`, este test fallaria (el error saldria en el primer render).
    @Test
    fun un_dialogo_recien_abierto_con_el_campo_vacio_no_muestra_error() {
        assertNull(shownDeckNameError(error = null, touched = false, name = ""))
    }

    // Mutacion N3: si nunca se mostrara el error de campo vacio, estos tests fallarian.
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

    // Mutacion N2: si el error de campo vacio mandara sobre el del VM, este test fallaria.
    @Test
    fun el_error_del_VM_manda_sobre_el_de_campo_vacio() {
        assertEquals(DeckNameError.Duplicate, shownDeckNameError(error = DeckNameError.Duplicate, touched = true, name = ""))
    }
}
