package org.braincards.app.presentation.screens.auth

import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.SignUpOutcome
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AuthViewModel: los errores de login y registro llegan como TIPO (campo o banner) en [AuthViewModel.uiError], sin
 * pasar por messageEvent; y los flujos de Ajustes (messageEvent del borrado de cuenta, cierre de sesion, sync
 * manual) siguen igual. Los errores de supabase con codigo (AuthRestException) se prueban en jvmTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelFormErrorsTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Recorded(
        val confirmEmails: MutableList<String>,
        val notConfirmedEmails: MutableList<String>
    )

    private fun TestScope.record(env: AuthVmEnv): Recorded {
        val r = Recorded(mutableListOf(), mutableListOf())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            env.vm.signUpConfirmationRequiredEvent.collect { r.confirmEmails += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            env.vm.emailNotConfirmedEvent.collect { r.notConfirmedEmails += it }
        }
        return r
    }

    private val network get() = HttpRequestException("fallo de red simulado", HttpRequestBuilder())

    // --- login -------------------------------------------------------------------------------------------------------

    @Test
    fun login_con_fallo_de_red_es_banner_con_reintentar() = runTest {
        val env = AuthVmEnv()
        val rec = record(env)
        env.auth.signInFailure = network

        env.vm.signIn("a@b.co", "secreto123")

        assertEquals(AuthFormTarget.Banner(AuthMessage.NetworkError, retryable = true), env.vm.uiError.value)
        assertTrue(rec.notConfirmedEmails.isEmpty())
    }

    @Test
    fun login_con_fallo_generico_es_banner_generico() = runTest {
        val env = AuthVmEnv()
        env.auth.signInFailure = IllegalStateException("x")

        env.vm.signIn("a@b.co", "secreto123")

        assertEquals(AuthFormTarget.Banner(AuthMessage.GenericError), env.vm.uiError.value)
    }

    @Test
    fun un_envio_nuevo_limpia_el_error_anterior() = runTest {
        val env = AuthVmEnv()
        env.auth.signInFailure = network
        env.vm.signIn("a@b.co", "secreto123")
        assertEquals(AuthMessage.NetworkError, (env.vm.uiError.value as AuthFormTarget.Banner).message)

        env.auth.signInFailure = null
        env.vm.signIn("a@b.co", "secreto123")

        assertNull(env.vm.uiError.value)
    }

    @Test
    fun clearError_limpia_el_error() = runTest {
        val env = AuthVmEnv()
        env.auth.signInFailure = network
        env.vm.signIn("a@b.co", "secreto123")

        env.vm.clearError()

        assertNull(env.vm.uiError.value)
    }

    @Test
    fun el_doble_tap_en_login_no_envia_dos_veces() = runTest {
        val env = AuthVmEnv()
        val gate = CompletableDeferred<Unit>()
        env.auth.signInGate = gate

        env.vm.signIn("a@b.co", "secreto123")
        assertTrue(env.vm.isSubmitting.value)
        env.vm.signIn("a@b.co", "secreto123")
        gate.complete(Unit)

        assertEquals(1, env.auth.signInCallCount)
    }

    // --- registro ----------------------------------------------------------------------------------------------------

    @Test
    fun registro_que_pide_confirmacion_emite_el_evento_con_el_correo_y_no_es_un_error() = runTest {
        val env = AuthVmEnv()
        val rec = record(env)
        env.auth.signUpOutcome = SignUpOutcome.ConfirmationRequired

        env.vm.signUp("nuevo@b.co", "secreto123")

        assertEquals(listOf("nuevo@b.co"), rec.confirmEmails)
        assertNull(env.vm.uiError.value)
    }

    @Test
    fun registro_con_fallo_de_red_es_banner_con_reintentar() = runTest {
        val env = AuthVmEnv()
        env.auth.signUpFailure = network

        env.vm.signUp("nuevo@b.co", "secreto123")

        assertEquals(AuthFormTarget.Banner(AuthMessage.NetworkError, retryable = true), env.vm.uiError.value)
    }

    // --- los flujos de Ajustes NO cambian ---------------------------------------------------------------------------

    @Test
    fun borrar_la_cuenta_ya_no_emite_el_Snackbar_de_Ajustes_y_no_toca_uiError() = runTest {
        // El aviso "Cuenta eliminada." lo muestra ahora la Bienvenida (AppEntryViewModel.welcomeNotice): el emit de
        // Ajustes no llegaba a verse porque la navegacion ocurre casi a la vez.
        val env = AuthVmEnv(AuthState.Authenticated("user-1", "a@b.co", sessionConfirmed = true))
        record(env)

        env.vm.deleteAccount()

        assertNull(env.vm.uiError.value)
    }

    @Test
    fun si_falla_el_borrado_de_cuenta_avisa_con_deleteAccountEvent() = runTest {
        val env = AuthVmEnv(AuthState.Authenticated("user-1", "a@b.co", sessionConfirmed = true))
        record(env)
        val events = mutableListOf<DeleteAccountEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.deleteAccountEvent.collect { events += it } }
        env.auth.deleteAccountFailure = IllegalStateException("x")

        env.vm.deleteAccount()

        assertEquals(listOf(DeleteAccountEvent.Failed), events)
        assertNull(env.vm.uiError.value)
    }

    @Test
    fun el_cierre_de_sesion_sin_pendientes_sigue_terminando_en_SignedOut() = runTest {
        val env = AuthVmEnv(AuthState.Authenticated("user-1", "a@b.co", sessionConfirmed = false))
        val events = mutableListOf<SignOutEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.signOutEvent.collect { events += it } }

        env.vm.requestSignOut()

        assertEquals(listOf(SignOutEvent.SignedOut), events)
        assertNull(env.vm.uiError.value)
    }

    @Test
    fun el_sync_manual_sigue_informando_por_syncMessageEvent() = runTest {
        val env = AuthVmEnv() // sin sesion: el sync manual devuelve NotAuthenticated
        val events = mutableListOf<SyncUserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.syncMessageEvent.collect { events += it } }

        env.vm.syncNow()

        assertEquals(listOf<SyncUserMessage>(SyncUserMessage.NotAuthenticated), events)
        assertNull(env.vm.uiError.value)
    }
}
