package org.braincards.app.presentation.screens.auth

import org.braincards.app.data.mapper.AuthErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lo puro de ConfirmEmail / ForgotPassword / ResetPassword: formato del cooldown, formulario de restablecer y reparto. */
class AuthAccountFormsPureTest {

    // --- cooldown ----------------------------------------------------------------------------------------------------

    @Test
    fun el_cooldown_se_formatea_como_m_ss() {
        assertEquals("0:38", formatCooldown(38))
        assertEquals("1:00", formatCooldown(60))
        assertEquals("0:05", formatCooldown(5))
        assertEquals("0:00", formatCooldown(0))
        assertEquals("2:03", formatCooldown(123))
    }

    @Test
    fun un_cooldown_negativo_se_trata_como_cero() {
        assertEquals("0:00", formatCooldown(-4))
    }

    // --- restablecer -------------------------------------------------------------------------------------------------

    @Test
    fun restablecer_con_todo_correcto_no_tiene_errores() {
        assertTrue(validateResetForm("123456", "12345678", "12345678").isEmpty)
    }

    @Test
    fun restablecer_con_los_tres_campos_mal_da_un_error_por_campo_como_el_mockup() {
        val errors = validateResetForm("", "1234567", "12345")
        assertEquals(AuthMessage.CodeIncomplete, errors[AuthField.CODE])
        assertEquals(AuthMessage.PasswordTooShort, errors[AuthField.PASSWORD])
        assertEquals(AuthMessage.PasswordsDontMatch, errors[AuthField.CONFIRM_PASSWORD])
    }

    @Test
    fun restablecer_con_contrasena_valida_pero_confirmacion_distinta_solo_falla_la_confirmacion() {
        val errors = validateResetForm("123456", "12345678", "12345679")
        assertNull(errors[AuthField.CODE])
        assertNull(errors[AuthField.PASSWORD])
        assertEquals(AuthMessage.PasswordsDontMatch, errors[AuthField.CONFIRM_PASSWORD])
    }

    @Test
    fun el_foco_va_al_primer_campo_con_error_en_el_orden_codigo_contrasena_confirmacion() {
        val order = listOf(AuthField.CODE, AuthField.PASSWORD, AuthField.CONFIRM_PASSWORD)
        assertEquals(AuthField.CODE, validateResetForm("12", "1", "2").firstIn(order))
        assertEquals(AuthField.PASSWORD, validateResetForm("123456", "1", "2").firstIn(order))
        assertEquals(AuthField.CONFIRM_PASSWORD, validateResetForm("123456", "12345678", "2").firstIn(order))
    }

    @Test
    fun un_codigo_con_letras_o_de_otra_longitud_es_incompleto() {
        assertEquals(AuthMessage.CodeIncomplete, validateResetForm("12345a", "12345678", "12345678")[AuthField.CODE])
        assertEquals(AuthMessage.CodeIncomplete, validateResetForm("1234567", "12345678", "12345678")[AuthField.CODE])
    }

    // --- reparto por contexto de las pantallas de cuenta -------------------------------------------------------------

    @Test
    fun codigo_caducado_solo_es_error_de_campo_al_confirmar_y_al_restablecer() {
        val field = AuthFormTarget.Field(AuthField.CODE, AuthMessage.CodeInvalidOrExpired)
        assertEquals(field, AuthErrorKind.OtpExpired.targetFor(AuthContext.CONFIRM_CODE))
        assertEquals(field, AuthErrorKind.OtpExpired.targetFor(AuthContext.RESET))
        listOf(AuthContext.RESEND_CODE, AuthContext.FORGOT, AuthContext.REGISTER, AuthContext.LOGIN).forEach {
            assertEquals(AuthFormTarget.Banner(AuthMessage.GenericError), AuthErrorKind.OtpExpired.targetFor(it), "$it")
        }
    }

    @Test
    fun contrasena_debil_en_restablecer_es_error_del_campo_contrasena_y_en_recuperar_banner() {
        assertEquals(
            AuthFormTarget.Field(AuthField.PASSWORD, AuthMessage.WeakPassword),
            AuthErrorKind.WeakPassword.targetFor(AuthContext.RESET)
        )
        assertEquals(
            AuthFormTarget.Banner(AuthMessage.GenericError),
            AuthErrorKind.WeakPassword.targetFor(AuthContext.FORGOT)
        )
    }
}
