package org.braincards.app.data.mapper

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

/** Clasificacion por TIPO (sin texto) de los fallos de auth: los 6 codigos reales, el generico y la red. */
class AuthErrorKindTest {

    @Test
    fun los_seis_codigos_reales() {
        assertEquals(AuthErrorKind.OtpExpired, authErrorKind(AuthErrorCode.OtpExpired))
        assertEquals(AuthErrorKind.WeakPassword, authErrorKind(AuthErrorCode.WeakPassword))
        assertEquals(AuthErrorKind.RateLimited, authErrorKind(AuthErrorCode.OverEmailSendRateLimit))
        assertEquals(AuthErrorKind.RateLimited, authErrorKind(AuthErrorCode.OverRequestRateLimit))
        assertEquals(AuthErrorKind.EmailNotConfirmed, authErrorKind(AuthErrorCode.EmailNotConfirmed))
        assertEquals(AuthErrorKind.InvalidCredentials, authErrorKind(AuthErrorCode.InvalidCredentials))
    }

    @Test
    fun cualquier_otro_codigo_o_ninguno_es_generico() {
        assertEquals(AuthErrorKind.Generic, authErrorKind(AuthErrorCode.UserNotFound))
        assertEquals(AuthErrorKind.Generic, authErrorKind(null))
    }

    @Test
    fun un_fallo_de_conexion_es_Network() {
        assertEquals(AuthErrorKind.Network, HttpRequestException("x", HttpRequestBuilder()).toAuthErrorKind())
    }

    @Test
    fun una_excepcion_cualquiera_es_generica() {
        assertEquals(AuthErrorKind.Generic, RuntimeException("x").toAuthErrorKind())
    }

    @Test
    fun el_mapeo_de_textos_antiguo_sigue_clasificando_igual_los_codigos() {
        // authErrorMessage (textos) y authErrorKind (tipos) deben cubrir los mismos codigos.
        assertEquals("Código incorrecto o caducado. Pide uno nuevo.", authErrorMessage(AuthErrorCode.OtpExpired))
        assertEquals(AuthErrorKind.OtpExpired, authErrorKind(AuthErrorCode.OtpExpired))
    }
}
