package org.braincards.app.presentation.screens.auth

import org.braincards.app.data.mapper.AuthErrorKind

/** Pantalla/flujo donde ocurre un fallo: el mismo tipo de error se reparte distinto segun el contexto. */
enum class AuthContext { LOGIN, REGISTER, CONFIRM_CODE, RESEND_CODE, FORGOT, RESET }

/** Campos de los formularios de auth (a los que se puede atribuir un error). */
enum class AuthField { EMAIL, PASSWORD, CONFIRM_PASSWORD, CODE }

/**
 * Clave de cada texto de error de los formularios de auth. La capa pura NO resuelve recursos: la UI traduce cada
 * clave con stringResource (ver AuthMessageStrings.kt).
 */
enum class AuthMessage {
    // Del servidor (AuthErrorKind)
    CodeInvalidOrExpired,
    WeakPassword,
    TooManyAttempts,
    InvalidCredentials,
    EmailNotConfirmed,
    NetworkError,
    GenericError,

    // Validaciones del cliente (AuthValidation.kt)
    EmailEmpty,
    EmailInvalid,
    PasswordEmpty,
    PasswordTooShort,
    PasswordsDontMatch,
    CodeIncomplete
}

/** Confirmaciones breves (Snackbar) de los formularios de auth. Clave: la UI la traduce con stringResource. */
enum class AuthNotice { CodeResent }

/** Donde se muestra un fallo de auth. */
sealed interface AuthFormTarget {
    /** Error de campo: bajo el input correspondiente. */
    data class Field(val field: AuthField, val message: AuthMessage) : AuthFormTarget

    /** Banner dentro del formulario; [retryable] = con "Reintentar" (solo la red). */
    data class Banner(val message: AuthMessage, val retryable: Boolean = false) : AuthFormTarget

    /** Login con cuenta sin confirmar: no es un error, se lleva al usuario a la pantalla del codigo. */
    data object GoToConfirmEmail : AuthFormTarget
}

private val GENERIC_BANNER = AuthFormTarget.Banner(AuthMessage.GenericError)

/**
 * Reparto de los fallos de auth (handoff v4.3, seccion 2): campo / banner / ir a confirmar. Un tipo que no puede
 * darse en un contexto (p. ej. credenciales invalidas fuera del login) cae en el banner generico.
 */
fun AuthErrorKind.targetFor(context: AuthContext): AuthFormTarget = when (this) {
    AuthErrorKind.OtpExpired -> when (context) {
        AuthContext.CONFIRM_CODE, AuthContext.RESET ->
            AuthFormTarget.Field(AuthField.CODE, AuthMessage.CodeInvalidOrExpired)
        else -> GENERIC_BANNER
    }
    AuthErrorKind.WeakPassword -> when (context) {
        AuthContext.REGISTER, AuthContext.RESET -> AuthFormTarget.Field(AuthField.PASSWORD, AuthMessage.WeakPassword)
        else -> GENERIC_BANNER
    }
    AuthErrorKind.RateLimited -> AuthFormTarget.Banner(AuthMessage.TooManyAttempts)
    AuthErrorKind.InvalidCredentials -> when (context) {
        AuthContext.LOGIN -> AuthFormTarget.Banner(AuthMessage.InvalidCredentials)
        else -> GENERIC_BANNER
    }
    AuthErrorKind.EmailNotConfirmed -> when (context) {
        AuthContext.LOGIN -> AuthFormTarget.GoToConfirmEmail
        else -> AuthFormTarget.Banner(AuthMessage.EmailNotConfirmed)
    }
    AuthErrorKind.Network -> AuthFormTarget.Banner(AuthMessage.NetworkError, retryable = true)
    AuthErrorKind.Generic -> GENERIC_BANNER
}
