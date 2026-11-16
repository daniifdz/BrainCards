package org.braincards.app.presentation.screens.auth

import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Flujos de ConfirmEmail / ForgotPassword / ResetPassword en AuthViewModel: errores como tipo en uiError (sin
 * messageEvent), cooldown de reenvio con tiempo virtual, y que ABRIR la pantalla del codigo nunca envia nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelAccountFlowsTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val network get() = HttpRequestException("fallo de red simulado", HttpRequestBuilder())

    private class Seen(
        val notices: MutableList<AuthNotice> = mutableListOf(),
        val confirmed: MutableList<Unit> = mutableListOf(),
        val resetCodeSent: MutableList<String> = mutableListOf(),
        val resetCompleted: MutableList<Unit> = mutableListOf()
    )

    private fun TestScope.record(env: AuthVmEnv): Seen {
        val s = Seen()
        fun <T> collectInto(flow: kotlinx.coroutines.flow.Flow<T>, sink: MutableList<T>) {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { sink += it } }
        }
        collectInto(env.vm.noticeEvent, s.notices)
        collectInto(env.vm.signUpConfirmedEvent, s.confirmed)
        collectInto(env.vm.passwordResetCodeSentEvent, s.resetCodeSent)
        collectInto(env.vm.passwordResetCompletedEvent, s.resetCompleted)
        return s
    }

    // --- ConfirmEmail: confirmar ------------------------------------------------------------------------------------

    @Test
    fun confirmar_con_exito_avisa_a_la_pantalla() = runTest {
        val env = AuthVmEnv()
        val seen = record(env)

        env.vm.confirmSignUp("a@b.co", "123456")

        assertEquals(1, seen.confirmed.size)
        assertNull(env.vm.uiError.value)
    }

    @Test
    fun confirmar_con_algo_que_no_son_digitos_es_error_del_campo_codigo() = runTest {
        val env = AuthVmEnv()

        env.vm.confirmSignUp("a@b.co", "12ab56")

        assertEquals(AuthFormTarget.Field(AuthField.CODE, AuthMessage.CodeIncomplete), env.vm.uiError.value)
    }

    @Test
    fun confirmar_sin_red_es_banner_con_reintentar() = runTest {
        val env = AuthVmEnv()
        env.auth.verifySignUpCodeFailure = network

        env.vm.confirmSignUp("a@b.co", "123456")

        assertEquals(AuthFormTarget.Banner(AuthMessage.NetworkError, retryable = true), env.vm.uiError.value)
    }

    @Test
    fun un_nuevo_intento_de_confirmar_limpia_el_error_anterior() = runTest {
        val env = AuthVmEnv()
        env.auth.verifySignUpCodeFailure = network
        env.vm.confirmSignUp("a@b.co", "123456")
        env.auth.verifySignUpCodeFailure = null

        env.vm.confirmSignUp("a@b.co", "123456")

        assertNull(env.vm.uiError.value)
    }

    // --- ConfirmEmail: reenviar y cooldown --------------------------------------------------------------------------

    @Test
    fun reenviar_con_exito_avisa_con_la_notificacion_y_arranca_el_cooldown() = runTest {
        val env = AuthVmEnv()
        val seen = record(env)

        env.vm.resendSignUpCode("a@b.co")

        assertEquals(listOf(AuthNotice.CodeResent), seen.notices)
        assertEquals(1, env.auth.resendSignUpCodeCallCount)
        assertEquals(RESEND_CODE_COOLDOWN_SECONDS, env.vm.resendCooldownSeconds.value)
    }

    @Test
    fun durante_el_cooldown_no_se_puede_reenviar() = runTest {
        val env = AuthVmEnv()
        env.vm.resendSignUpCode("a@b.co")

        env.vm.resendSignUpCode("a@b.co")

        assertEquals(1, env.auth.resendSignUpCodeCallCount)
    }

    @Test
    fun el_cooldown_baja_cada_segundo_y_a_los_60_s_permite_reenviar_otra_vez() = runTest {
        val env = AuthVmEnv()
        env.vm.resendSignUpCode("a@b.co")

        advanceTimeBy(1_000); runCurrent()
        assertEquals(RESEND_CODE_COOLDOWN_SECONDS - 1, env.vm.resendCooldownSeconds.value)

        advanceTimeBy(59_000); runCurrent()
        assertEquals(0, env.vm.resendCooldownSeconds.value)

        env.vm.resendSignUpCode("a@b.co")
        assertEquals(2, env.auth.resendSignUpCodeCallCount)
    }

    @Test
    fun si_el_reenvio_falla_no_hay_cooldown_ni_notificacion() = runTest {
        val env = AuthVmEnv()
        val seen = record(env)
        env.auth.resendSignUpCodeFailure = network

        env.vm.resendSignUpCode("a@b.co")

        assertEquals(AuthFormTarget.Banner(AuthMessage.NetworkError, retryable = true), env.vm.uiError.value)
        assertEquals(0, env.vm.resendCooldownSeconds.value)
        assertTrue(seen.notices.isEmpty())
    }

    // --- Abrir la pantalla del codigo NO envia nada -----------------------------------------------------------------

    @Test
    fun abrir_la_pantalla_tras_registrarse_solo_arranca_el_cooldown_sin_enviar_ningun_codigo() = runTest {
        val env = AuthVmEnv()

        env.vm.startInitialResendCooldown() // lo que hace ConfirmEmailScreen con codeJustSent = true

        assertEquals(RESEND_CODE_COOLDOWN_SECONDS, env.vm.resendCooldownSeconds.value)
        assertEquals(0, env.auth.resendSignUpCodeCallCount, "no reenvia")
        assertEquals(0, env.auth.signUpCallCount, "no vuelve a registrar")
        assertEquals(0, env.auth.verifySignUpCodeCallCount)
    }

    @Test
    fun el_cooldown_inicial_es_idempotente_volver_a_componer_la_pantalla_no_lo_reinicia() = runTest {
        val env = AuthVmEnv()
        env.vm.startInitialResendCooldown()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(RESEND_CODE_COOLDOWN_SECONDS - 10, env.vm.resendCooldownSeconds.value)

        env.vm.startInitialResendCooldown() // p. ej. rotacion: la pantalla se compone otra vez

        assertEquals(RESEND_CODE_COOLDOWN_SECONDS - 10, env.vm.resendCooldownSeconds.value)
        assertEquals(0, env.auth.resendSignUpCodeCallCount)
    }

    @Test
    fun volver_a_entrar_en_ConfirmEmail_con_otra_instancia_no_envia_nada_ni_arranca_cooldown() = runTest {
        // Auth -> ConfirmEmail -> atras -> de nuevo (p. ej. login sin confirmar: codeJustSent = false): el
        // ViewModel nuevo no recibe ninguna orden de enviar; la pantalla no llama a startInitialResendCooldown.
        val first = AuthVmEnv()
        first.vm.startInitialResendCooldown()
        val second = AuthVmEnv()

        assertEquals(0, second.vm.resendCooldownSeconds.value)
        assertEquals(0, second.auth.resendSignUpCodeCallCount)
        assertEquals(0, second.auth.signUpCallCount)
        assertEquals(0, first.auth.resendSignUpCodeCallCount)
    }

    // --- ForgotPassword ---------------------------------------------------------------------------------------------

    @Test
    fun enviar_el_codigo_de_recuperacion_emite_el_evento_con_el_correo() = runTest {
        val env = AuthVmEnv()
        val seen = record(env)

        env.vm.sendPasswordReset("a@b.co")

        assertEquals(listOf("a@b.co"), seen.resetCodeSent)
        assertEquals(1, env.auth.sendPasswordResetCallCount)
        assertNull(env.vm.uiError.value)
    }

    @Test
    fun enviar_el_codigo_sin_red_es_banner_con_reintentar_y_no_navega() = runTest {
        val env = AuthVmEnv()
        val seen = record(env)
        env.auth.sendPasswordResetFailure = network

        env.vm.sendPasswordReset("a@b.co")

        assertEquals(AuthFormTarget.Banner(AuthMessage.NetworkError, retryable = true), env.vm.uiError.value)
        assertTrue(seen.resetCodeSent.isEmpty())
    }

    @Test
    fun correo_vacio_en_recuperar_es_error_del_campo_correo() = runTest {
        val env = AuthVmEnv()

        env.vm.sendPasswordReset("  ")

        assertEquals(AuthFormTarget.Field(AuthField.EMAIL, AuthMessage.EmailEmpty), env.vm.uiError.value)
    }

    // --- ResetPassword ----------------------------------------------------------------------------------------------

    @Test
    fun restablecer_con_exito_avisa_a_la_pantalla() = runTest {
        val env = AuthVmEnv()
        val seen = record(env)

        env.vm.confirmPasswordReset("a@b.co", "123456", "nueva-clave-1")

        assertEquals(1, seen.resetCompleted.size)
        assertNull(env.vm.uiError.value)
    }

    @Test
    fun restablecer_con_fallo_generico_es_banner_generico() = runTest {
        val env = AuthVmEnv()
        env.auth.updatePasswordFailure = IllegalStateException("x")

        env.vm.confirmPasswordReset("a@b.co", "123456", "nueva-clave-1")

        assertEquals(AuthFormTarget.Banner(AuthMessage.GenericError), env.vm.uiError.value)
    }

    @Test
    fun restablecer_con_el_codigo_ya_verificado_y_la_contrasena_rechazada_marca_codeVerified() = runTest {
        val env = AuthVmEnv()
        env.auth.updatePasswordFailure = network

        env.vm.confirmPasswordReset("a@b.co", "123456", "nueva-clave-1")

        assertTrue(env.vm.codeVerified.value)
        assertEquals(AuthFormTarget.Banner(AuthMessage.NetworkError, retryable = true), env.vm.uiError.value)
    }
}
