package org.braincards.app.data.mapper

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AuthRestException/RestException exigen un HttpResponse real en el constructor
 * (necesita un engine HTTP) -- no es construible a mano barato, igual que
 * RefreshFailureCause.InternalServerError en AuthStateMapperTest. Por eso el
 * nucleo puro (authErrorMessage) se testea directo con los AuthErrorCode reales,
 * y el wrapper (toAuthErrorMessage) solo en sus ramas baratas de construir.
 */
class AuthErrorMapperTest {

    @Test
    fun `OtpExpired devuelve mensaje de codigo incorrecto o caducado`() {
        assertEquals(
            "Código incorrecto o caducado. Pide uno nuevo.",
            authErrorMessage(AuthErrorCode.OtpExpired)
        )
    }

    @Test
    fun `WeakPassword devuelve mensaje de contraseña debil`() {
        assertEquals(
            "La contraseña es demasiado débil. Prueba con una más larga o con más variedad de caracteres.",
            authErrorMessage(AuthErrorCode.WeakPassword)
        )
    }

    @Test
    fun `OverEmailSendRateLimit devuelve mensaje de demasiados codigos`() {
        assertEquals(
            "Has pedido demasiados códigos. Espera un poco antes de volver a intentarlo.",
            authErrorMessage(AuthErrorCode.OverEmailSendRateLimit)
        )
    }

    @Test
    fun `OverRequestRateLimit devuelve mensaje de demasiados intentos`() {
        assertEquals(
            "Demasiados intentos. Espera un poco antes de volver a intentarlo.",
            authErrorMessage(AuthErrorCode.OverRequestRateLimit)
        )
    }

    @Test
    fun `codigo desconocido o nulo devuelve mensaje generico`() {
        assertEquals("Algo salió mal. Inténtalo de nuevo.", authErrorMessage(null))
        assertEquals("Algo salió mal. Inténtalo de nuevo.", authErrorMessage(AuthErrorCode.UserNotFound))
    }

    @Test
    fun `HttpRequestException se mapea a mensaje de conexion`() {
        val exception = HttpRequestException("fallo de red simulado", HttpRequestBuilder())

        assertEquals(
            "No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.",
            exception.toAuthErrorMessage()
        )
    }

    @Test
    fun `excepcion generica devuelve mensaje generico`() {
        assertEquals(
            "Algo salió mal. Inténtalo de nuevo.",
            RuntimeException("cualquier cosa").toAuthErrorMessage()
        )
    }
}
