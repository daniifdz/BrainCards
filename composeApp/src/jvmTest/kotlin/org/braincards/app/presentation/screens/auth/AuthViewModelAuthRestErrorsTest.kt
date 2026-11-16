package org.braincards.app.presentation.screens.auth

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.braincards.app.data.mapper.isEmailNotConfirmed
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Errores de supabase CON codigo (AuthRestException) a traves del AuthViewModel real. El constructor de
 * AuthRestException exige un HttpResponse de ktor, imposible de fabricar sin dependencias nuevas: aqui (solo JVM)
 * se crea la instancia SIN llamar al constructor (Unsafe.allocateInstance) y se fija su `errorCode` por reflexion.
 * Es lo unico que lee el codigo de produccion de ella (errorCode); logError solo usa la clase y statusCode (0).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelAuthRestErrorsTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun authRest(code: AuthErrorCode?): AuthRestException {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val exception = unsafe.allocateInstance(AuthRestException::class.java) as AuthRestException
        AuthRestException::class.java.getDeclaredField("errorCode").apply { isAccessible = true }.set(exception, code)
        return exception
    }

    @Test
    fun la_excepcion_de_prueba_se_clasifica_como_la_real() {
        assertTrue(authRest(AuthErrorCode.EmailNotConfirmed).isEmailNotConfirmed())
        assertEquals(false, authRest(AuthErrorCode.InvalidCredentials).isEmailNotConfirmed())
    }

    @Test
    fun login_con_credenciales_invalidas_es_banner() = runTest {
        val env = AuthVmEnv()
        env.auth.signInFailure = authRest(AuthErrorCode.InvalidCredentials)

        env.vm.signIn("a@b.co", "secreto123")

        assertEquals(AuthFormTarget.Banner(AuthMessage.InvalidCredentials), env.vm.uiError.value)
    }

    @Test
    fun login_con_cuenta_sin_confirmar_emite_el_evento_con_el_correo_y_NO_es_un_error() = runTest {
        val env = AuthVmEnv()
        val notConfirmed = mutableListOf<String>()
        val confirmRequired = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.emailNotConfirmedEvent.collect { notConfirmed += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.signUpConfirmationRequiredEvent.collect { confirmRequired += it } }
        env.auth.signInFailure = authRest(AuthErrorCode.EmailNotConfirmed)

        env.vm.signIn("a@b.co", "secreto123")

        // Como siempre: evento de "email sin confirmar" (App.kt navega a ConfirmEmail con codeJustSent = false).
        assertEquals(listOf("a@b.co"), notConfirmed)
        assertTrue(confirmRequired.isEmpty(), "no es el evento de registro (codeJustSent = true)")
        assertNull(env.vm.uiError.value)
    }

    @Test
    fun login_con_limite_de_peticiones_es_banner_de_demasiados_intentos() = runTest {
        val env = AuthVmEnv()
        env.auth.signInFailure = authRest(AuthErrorCode.OverRequestRateLimit)

        env.vm.signIn("a@b.co", "secreto123")

        assertEquals(AuthFormTarget.Banner(AuthMessage.TooManyAttempts), env.vm.uiError.value)
    }

    @Test
    fun registro_con_contrasena_debil_del_servidor_es_error_del_campo_contrasena() = runTest {
        val env = AuthVmEnv()
        env.auth.signUpFailure = authRest(AuthErrorCode.WeakPassword)

        env.vm.signUp("nuevo@b.co", "12345678")

        assertEquals(AuthFormTarget.Field(AuthField.PASSWORD, AuthMessage.WeakPassword), env.vm.uiError.value)
    }

    @Test
    fun registro_con_limite_de_emails_es_banner() = runTest {
        val env = AuthVmEnv()
        env.auth.signUpFailure = authRest(AuthErrorCode.OverEmailSendRateLimit)

        env.vm.signUp("nuevo@b.co", "12345678")

        assertEquals(AuthFormTarget.Banner(AuthMessage.TooManyAttempts), env.vm.uiError.value)
    }

    @Test
    fun registro_con_un_codigo_que_no_aplica_cae_en_el_banner_generico() = runTest {
        val env = AuthVmEnv()
        env.auth.signUpFailure = authRest(AuthErrorCode.InvalidCredentials)

        env.vm.signUp("nuevo@b.co", "12345678")

        assertEquals(AuthFormTarget.Banner(AuthMessage.GenericError), env.vm.uiError.value)
    }

    // --- ConfirmEmail / ForgotPassword / ResetPassword -------------------------------------------------------------

    @Test
    fun confirmar_con_codigo_caducado_es_error_del_campo_codigo() = runTest {
        val env = AuthVmEnv()
        env.auth.verifySignUpCodeFailure = authRest(AuthErrorCode.OtpExpired)

        env.vm.confirmSignUp("a@b.co", "123456")

        assertEquals(AuthFormTarget.Field(AuthField.CODE, AuthMessage.CodeInvalidOrExpired), env.vm.uiError.value)
    }

    @Test
    fun reenviar_con_limite_de_emails_es_banner_y_no_arranca_el_cooldown() = runTest {
        val env = AuthVmEnv()
        env.auth.resendSignUpCodeFailure = authRest(AuthErrorCode.OverEmailSendRateLimit)

        env.vm.resendSignUpCode("a@b.co")

        assertEquals(AuthFormTarget.Banner(AuthMessage.TooManyAttempts), env.vm.uiError.value)
        assertEquals(0, env.vm.resendCooldownSeconds.value)
    }

    @Test
    fun recuperar_con_limite_de_peticiones_es_banner_y_no_navega() = runTest {
        val env = AuthVmEnv()
        val sent = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.passwordResetCodeSentEvent.collect { sent += it } }
        env.auth.sendPasswordResetFailure = authRest(AuthErrorCode.OverRequestRateLimit)

        env.vm.sendPasswordReset("a@b.co")

        assertEquals(AuthFormTarget.Banner(AuthMessage.TooManyAttempts), env.vm.uiError.value)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun restablecer_con_codigo_caducado_es_error_del_campo_codigo() = runTest {
        val env = AuthVmEnv()
        env.auth.verifyPasswordResetCodeFailure = authRest(AuthErrorCode.OtpExpired)

        env.vm.confirmPasswordReset("a@b.co", "123456", "nueva-clave-1")

        assertEquals(AuthFormTarget.Field(AuthField.CODE, AuthMessage.CodeInvalidOrExpired), env.vm.uiError.value)
    }

    @Test
    fun restablecer_con_contrasena_debil_del_servidor_es_error_del_campo_contrasena() = runTest {
        val env = AuthVmEnv()
        env.auth.updatePasswordFailure = authRest(AuthErrorCode.WeakPassword)

        env.vm.confirmPasswordReset("a@b.co", "123456", "12345678")

        assertEquals(AuthFormTarget.Field(AuthField.PASSWORD, AuthMessage.WeakPassword), env.vm.uiError.value)
        assertTrue(env.vm.codeVerified.value, "el codigo ya se verifico: no hay que reescribirlo")
    }
}
