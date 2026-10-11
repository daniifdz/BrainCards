package org.braincards.app.domain.usecase.Auth

/**
 * Orquesta la recuperacion de contraseña por codigo OTP: verificar el codigo (de
 * un solo uso) y fijar la contraseña nueva. Guarda el email del ultimo codigo
 * verificado con exito para que, si updatePassword falla y se reintenta, no se
 * vuelva a llamar a verify con un codigo que el servidor ya consumio.
 */
class ConfirmPasswordResetUseCase(
    private val verifyPasswordResetCodeUseCase: VerifyPasswordResetCodeUseCase,
    private val updatePasswordUseCase: UpdatePasswordUseCase
) {
    private var verifiedEmail: String? = null

    suspend operator fun invoke(email: String, code: String, newPassword: String) {
        if (verifiedEmail != email) {
            verifyPasswordResetCodeUseCase(email, code)
            verifiedEmail = email
        }
        updatePasswordUseCase(newPassword)
        verifiedEmail = null
    }

    fun isCodeVerified(email: String): Boolean = verifiedEmail == email

    /** Llamar al pedir un codigo nuevo (envio inicial o reenvio) -- invalida
     * cualquier verificacion previa, porque ya no corresponde al codigo pedido. */
    fun onNewCodeRequested() {
        verifiedEmail = null
    }
}
