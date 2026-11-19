package org.braincards.app.presentation.screens.auth

/** Longitud minima de la contraseña al REGISTRARSE o restablecerla (el login no la exige: cuentas antiguas). */
const val MIN_PASSWORD_LENGTH = 8

/** Longitud del codigo de confirmacion que envia el servidor (6 digitos). */
const val CODE_LENGTH = 6

/**
 * Correo: se recorta (trim) y se comprueba lo mínimo; el servidor decide si es válido de verdad. Criterio:
 * - vacío -> [AuthMessage.EmailEmpty]
 * - inválido -> [AuthMessage.EmailInvalid] si tiene espacios por dentro, no tiene exactamente una '@', la parte local o el
 *   dominio están vacíos, o el dominio no tiene un punto con texto a ambos lados («usuario@ejemplo» es inválido). Nada de
 *   RFC: no se rechazan '+', guiones, subdominios ni TLD largos.
 * Devuelve null si pasa.
 */
fun validateEmail(raw: String): AuthMessage? {
    val email = raw.trim()
    if (email.isEmpty()) return AuthMessage.EmailEmpty
    if (email.any { it.isWhitespace() }) return AuthMessage.EmailInvalid
    if (email.count { it == '@' } != 1) return AuthMessage.EmailInvalid
    val local = email.substringBefore('@')
    val domain = email.substringAfter('@')
    if (local.isEmpty() || domain.isEmpty()) return AuthMessage.EmailInvalid
    val dot = domain.indexOf('.')
    if (dot <= 0 || domain.endsWith('.')) return AuthMessage.EmailInvalid
    return null
}

/** Contraseña nueva (registro / restablecer): no vacia y de al menos [MIN_PASSWORD_LENGTH]. NO se recorta. */
fun validateNewPassword(password: String): AuthMessage? = when {
    password.isEmpty() -> AuthMessage.PasswordEmpty
    password.length < MIN_PASSWORD_LENGTH -> AuthMessage.PasswordTooShort
    else -> null
}

/** Contraseña de login: solo que no este vacia (no se exige longitud: una cuenta antigua puede tener menos de 8). */
fun validateLoginPassword(password: String): AuthMessage? =
    if (password.isEmpty()) AuthMessage.PasswordEmpty else null

fun validateConfirmation(password: String, confirmation: String): AuthMessage? =
    if (password != confirmation) AuthMessage.PasswordsDontMatch else null

/** Codigo: exactamente [CODE_LENGTH] digitos. */
fun validateCode(code: String): AuthMessage? =
    if (code.length == CODE_LENGTH && code.all { it.isDigit() }) null else AuthMessage.CodeIncomplete

/** Errores de campo del cliente. Inmutable: [without] quita el de un campo al editarlo. */
data class FieldErrors(val errors: Map<AuthField, AuthMessage> = emptyMap()) {
    val isEmpty: Boolean get() = errors.isEmpty()
    operator fun get(field: AuthField): AuthMessage? = errors[field]
    fun without(field: AuthField) = FieldErrors(errors - field)

    /** Primer campo con error segun [order] (el orden de foco del formulario), o null. */
    fun firstIn(order: List<AuthField>): AuthField? = order.firstOrNull { it in errors }

    companion object {
        val None = FieldErrors()
    }
}

private fun errorsOf(vararg pairs: Pair<AuthField, AuthMessage?>) =
    FieldErrors(pairs.mapNotNull { (field, message) -> message?.let { field to it } }.toMap())

fun validateLoginForm(email: String, password: String) = errorsOf(
    AuthField.EMAIL to validateEmail(email),
    AuthField.PASSWORD to validateLoginPassword(password)
)

fun validateRegisterForm(email: String, password: String) = errorsOf(
    AuthField.EMAIL to validateEmail(email),
    AuthField.PASSWORD to validateNewPassword(password)
)

/** Formulario de restablecer contraseña: código + contraseña nueva + confirmación (los tres a la vez). */
fun validateResetForm(code: String, password: String, confirmation: String) = errorsOf(
    AuthField.CODE to validateCode(code),
    AuthField.PASSWORD to validateNewPassword(password),
    AuthField.CONFIRM_PASSWORD to validateConfirmation(password, confirmation)
)

/** Tiempo de espera del boton de reenvio como "m:ss" ("0:38", "1:00"). Negativos se tratan como 0. */
fun formatCooldown(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
