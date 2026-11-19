package org.braincards.app.presentation.screens.auth

import org.braincards.app.data.mapper.AuthErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals

/** Reparto de los fallos de auth entre campo / banner / ir a confirmar, por contexto. */
class AuthFormErrorTest {

    private val genericBanner = AuthFormTarget.Banner(AuthMessage.GenericError)

    @Test
    fun codigo_caducado_es_error_del_campo_codigo_al_confirmar_y_al_restablecer() {
        val expected = AuthFormTarget.Field(AuthField.CODE, AuthMessage.CodeInvalidOrExpired)
        assertEquals(expected, AuthErrorKind.OtpExpired.targetFor(AuthContext.CONFIRM_CODE))
        assertEquals(expected, AuthErrorKind.OtpExpired.targetFor(AuthContext.RESET))
    }

    @Test
    fun contrasena_debil_es_error_del_campo_contrasena_al_registrar_y_al_restablecer() {
        val expected = AuthFormTarget.Field(AuthField.PASSWORD, AuthMessage.WeakPassword)
        assertEquals(expected, AuthErrorKind.WeakPassword.targetFor(AuthContext.REGISTER))
        assertEquals(expected, AuthErrorKind.WeakPassword.targetFor(AuthContext.RESET))
    }

    @Test
    fun el_limite_de_envios_es_banner_sin_reintentar_en_cualquier_contexto() {
        val expected = AuthFormTarget.Banner(AuthMessage.TooManyAttempts, retryable = false)
        AuthContext.entries.forEach { assertEquals(expected, AuthErrorKind.RateLimited.targetFor(it), "$it") }
    }

    @Test
    fun credenciales_invalidas_es_banner_en_el_login() {
        assertEquals(
            AuthFormTarget.Banner(AuthMessage.InvalidCredentials),
            AuthErrorKind.InvalidCredentials.targetFor(AuthContext.LOGIN)
        )
    }

    @Test
    fun cuenta_sin_confirmar_en_el_login_lleva_a_confirmar_el_correo_y_no_es_un_error() {
        assertEquals(AuthFormTarget.GoToConfirmEmail, AuthErrorKind.EmailNotConfirmed.targetFor(AuthContext.LOGIN))
    }

    @Test
    fun cuenta_sin_confirmar_fuera_del_login_es_banner() {
        val expected = AuthFormTarget.Banner(AuthMessage.EmailNotConfirmed)
        listOf(AuthContext.REGISTER, AuthContext.CONFIRM_CODE, AuthContext.RESEND_CODE, AuthContext.FORGOT, AuthContext.RESET)
            .forEach { assertEquals(expected, AuthErrorKind.EmailNotConfirmed.targetFor(it), "$it") }
    }

    @Test
    fun la_red_es_banner_con_reintentar_en_cualquier_contexto() {
        val expected = AuthFormTarget.Banner(AuthMessage.NetworkError, retryable = true)
        AuthContext.entries.forEach { assertEquals(expected, AuthErrorKind.Network.targetFor(it), "$it") }
    }

    @Test
    fun el_generico_es_banner_sin_reintentar() {
        AuthContext.entries.forEach { assertEquals(genericBanner, AuthErrorKind.Generic.targetFor(it), "$it") }
    }

    @Test
    fun un_tipo_que_no_puede_darse_en_ese_contexto_cae_en_el_banner_generico() {
        assertEquals(genericBanner, AuthErrorKind.InvalidCredentials.targetFor(AuthContext.REGISTER))
        assertEquals(genericBanner, AuthErrorKind.InvalidCredentials.targetFor(AuthContext.FORGOT))
        assertEquals(genericBanner, AuthErrorKind.OtpExpired.targetFor(AuthContext.LOGIN))
        assertEquals(genericBanner, AuthErrorKind.WeakPassword.targetFor(AuthContext.LOGIN))
    }

    @Test
    fun solo_la_red_es_reintentable() {
        val retryable = AuthErrorKind.entries.filter { kind ->
            AuthContext.entries.any { (kind.targetFor(it) as? AuthFormTarget.Banner)?.retryable == true }
        }
        assertEquals(listOf(AuthErrorKind.Network), retryable)
    }
}
