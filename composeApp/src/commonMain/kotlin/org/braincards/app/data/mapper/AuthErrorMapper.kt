package org.braincards.app.data.mapper

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException

private const val GENERIC_AUTH_ERROR_MESSAGE = "Algo salió mal. Inténtalo de nuevo."

/**
 * Nucleo puro de la clasificacion -- sin dependencias de red ni de excepciones
 * dificiles de construir (ver AuthErrorMapperTest), para poder testear los 4
 * AuthErrorCode reales (confirmados en AuthErrorCode.kt) sin fabricar un
 * AuthRestException de verdad.
 */
fun authErrorMessage(errorCode: AuthErrorCode?): String = when (errorCode) {
    AuthErrorCode.OtpExpired -> "Código incorrecto o caducado. Pide uno nuevo."
    AuthErrorCode.WeakPassword -> "La contraseña es demasiado débil. Prueba con una más larga o con más variedad de caracteres."
    AuthErrorCode.OverEmailSendRateLimit -> "Has pedido demasiados códigos. Espera un poco antes de volver a intentarlo."
    AuthErrorCode.OverRequestRateLimit -> "Demasiados intentos. Espera un poco antes de volver a intentarlo."
    AuthErrorCode.EmailNotConfirmed -> "Tu cuenta aún no está confirmada. Escribe el código que te enviamos."
    AuthErrorCode.InvalidCredentials -> "Email o contraseña incorrectos."
    else -> GENERIC_AUTH_ERROR_MESSAGE
}

/** Nucleo puro de [isEmailNotConfirmed], testeable sin construir un AuthRestException. */
fun isEmailNotConfirmedCode(errorCode: AuthErrorCode?): Boolean =
    errorCode == AuthErrorCode.EmailNotConfirmed

/**
 * true si el login fallo porque la cuenta existe pero su email aun no esta
 * confirmado -- AuthViewModel lo usa para llevar a la pantalla del codigo en vez
 * de mostrar un error.
 */
fun Throwable.isEmailNotConfirmed(): Boolean =
    this is AuthRestException && isEmailNotConfirmedCode(errorCode)

/**
 * Mapeo completo Throwable -> mensaje de usuario, reutilizado tanto en el flujo
 * de verificar el codigo como en el de enviar/reenviar el email de recuperacion
 * -- un solo sitio donde se decide el texto, sin duplicar el `when`.
 */
fun Throwable.toAuthErrorMessage(): String = when (this) {
    is AuthRestException -> authErrorMessage(errorCode)
    is HttpRequestException, is HttpRequestTimeoutException ->
        "No se pudo conectar. Revisa tu conexión e inténtalo de nuevo."
    else -> GENERIC_AUTH_ERROR_MESSAGE
}
