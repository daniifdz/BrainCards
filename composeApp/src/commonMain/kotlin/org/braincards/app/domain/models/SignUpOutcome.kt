package org.braincards.app.domain.models

/**
 * Resultado de un registro con email y contraseña. Depende de si el proyecto de
 * Supabase exige confirmar el email: con confirmacion obligatoria no hay sesion
 * hasta que el usuario escribe el codigo que recibe por correo.
 */
enum class SignUpOutcome {
    /** El servidor devolvio sesion directamente (confirmacion de email desactivada). */
    SessionStarted,

    /** No hay sesion todavia: hay que verificar el codigo enviado por email. */
    ConfirmationRequired
}
