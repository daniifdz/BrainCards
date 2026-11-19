package org.braincards.app.presentation.screens.study

import androidx.compose.ui.input.key.Key
import org.braincards.app.domain.models.ReviewQuality
import kotlin.test.Test
import kotlin.test.assertEquals

/** Decisión pura del teclado de la sesión de estudio: no hace falta compose.uiTest. */
class StudyKeysTest {

    private fun action(key: Key, answerVisible: Boolean, ctrl: Boolean = false, alt: Boolean = false, meta: Boolean = false) =
        studyKeyAction(key, ctrl, alt, meta, answerVisible)

    private val reveal = StudyKeyAction.Reveal
    private val ignore = StudyKeyAction.Ignore
    private fun rate(q: ReviewQuality) = StudyKeyAction.Rate(q)

    @Test
    fun con_la_pregunta_visible_espacio_y_enter_revelan_tambien_el_del_teclado_numerico() {
        assertEquals(reveal, action(Key.Spacebar, answerVisible = false))
        assertEquals(reveal, action(Key.Enter, answerVisible = false))
        assertEquals(reveal, action(Key.NumPadEnter, answerVisible = false))
    }

    @Test
    fun con_la_pregunta_visible_los_numeros_no_puntuan() {
        listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4)
            .forEach { assertEquals(ignore, action(it, answerVisible = false), "tecla $it") }
    }

    @Test
    fun con_la_respuesta_visible_1_2_3_4_puntuan_otra_vez_dificil_bien_facil() {
        assertEquals(rate(ReviewQuality.AGAIN), action(Key.One, answerVisible = true))
        assertEquals(rate(ReviewQuality.HARD), action(Key.Two, answerVisible = true))
        assertEquals(rate(ReviewQuality.GOOD), action(Key.Three, answerVisible = true))
        assertEquals(rate(ReviewQuality.EASY), action(Key.Four, answerVisible = true))
    }

    @Test
    fun el_teclado_numerico_puntua_igual_que_la_fila_de_numeros() {
        assertEquals(rate(ReviewQuality.AGAIN), action(Key.NumPad1, answerVisible = true))
        assertEquals(rate(ReviewQuality.HARD), action(Key.NumPad2, answerVisible = true))
        assertEquals(rate(ReviewQuality.GOOD), action(Key.NumPad3, answerVisible = true))
        assertEquals(rate(ReviewQuality.EASY), action(Key.NumPad4, answerVisible = true))
    }

    @Test
    fun otros_numeros_y_teclas_no_hacen_nada() {
        listOf(Key.Zero, Key.Five, Key.NumPad0, Key.NumPad5, Key.A, Key.Tab, Key.DirectionRight).forEach {
            assertEquals(ignore, action(it, answerVisible = true), "tecla $it")
            assertEquals(ignore, action(it, answerVisible = false), "tecla $it")
        }
    }

    @Test
    fun con_la_respuesta_visible_espacio_y_enter_se_ignoran_para_no_robarselos_a_un_boton_con_foco() {
        assertEquals(ignore, action(Key.Spacebar, answerVisible = true))
        assertEquals(ignore, action(Key.Enter, answerVisible = true))
        assertEquals(ignore, action(Key.NumPadEnter, answerVisible = true))
    }

    @Test
    fun esc_sale_en_los_dos_estados() {
        assertEquals(StudyKeyAction.Exit, action(Key.Escape, answerVisible = false))
        assertEquals(StudyKeyAction.Exit, action(Key.Escape, answerVisible = true))
    }

    @Test
    fun cualquier_combinacion_con_ctrl_alt_o_meta_se_ignora() {
        for (visible in listOf(false, true)) {
            val keys = listOf(Key.Spacebar, Key.Enter, Key.One, Key.Two, Key.Three, Key.Four, Key.NumPad3, Key.Escape)
            for (key in keys) {
                assertEquals(ignore, action(key, visible, ctrl = true), "Ctrl+$key visible=$visible")
                assertEquals(ignore, action(key, visible, alt = true), "Alt+$key visible=$visible")
                assertEquals(ignore, action(key, visible, meta = true), "Meta+$key visible=$visible")
            }
        }
    }

    @Test
    fun sin_tarjeta_solo_vale_esc_y_no_se_roban_espacio_ni_enter_a_los_botones() {
        assertEquals(ignore, gateStudyKeyAction(reveal, cardShown = false))
        assertEquals(ignore, gateStudyKeyAction(rate(ReviewQuality.GOOD), cardShown = false))
        assertEquals(StudyKeyAction.Exit, gateStudyKeyAction(StudyKeyAction.Exit, cardShown = false))
    }

    @Test
    fun con_tarjeta_el_gate_deja_pasar_todo() {
        assertEquals(reveal, gateStudyKeyAction(reveal, cardShown = true))
        assertEquals(rate(ReviewQuality.EASY), gateStudyKeyAction(rate(ReviewQuality.EASY), cardShown = true))
        assertEquals(ignore, gateStudyKeyAction(ignore, cardShown = true))
    }
}
