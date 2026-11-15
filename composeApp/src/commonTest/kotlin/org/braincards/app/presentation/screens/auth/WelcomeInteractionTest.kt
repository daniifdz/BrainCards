package org.braincards.app.presentation.screens.auth

import org.braincards.app.presentation.screens.auth.WelcomeInteraction.*
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tabla de v4.9: que descarta el aviso persistente de sesion caducada y que no. */
class WelcomeInteractionTest {

    private val expected: Map<WelcomeInteraction, Boolean> = mapOf(
        EmailEdited to true,
        PasswordEdited to true,
        SubmitByButton to true,
        SubmitByIme to true,
        ToggleMode to true,
        ForgotPassword to true,
        ContinueWithoutAccount to true,
        Authenticated to true,
        FocusGainedWithoutTyping to false,
        Scrolled to false,
        Rotated to false,
        ThemeChanged to false
    )

    @Test
    fun la_tabla_cubre_todos_los_eventos() {
        // Un evento nuevo en el enum obliga a decidir aqui si descarta o no.
        assertEquals(WelcomeInteraction.entries.toSet(), expected.keys)
    }

    @Test
    fun cada_evento_descarta_o_no_segun_la_tabla() {
        val actual = WelcomeInteraction.entries.associateWith { isDismissingInteraction(it) }
        assertEquals(expected, actual)
    }

    @Test
    fun ForgotPassword_descarta() = assertEquals(true, isDismissingInteraction(ForgotPassword))

    @Test
    fun foco_scroll_rotar_y_cambiar_tema_no_descartan() {
        listOf(FocusGainedWithoutTyping, Scrolled, Rotated, ThemeChanged).forEach {
            assertEquals(false, isDismissingInteraction(it), "$it no debe descartar")
        }
    }
}
