package org.braincards.app.domain.usecase.Auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeLocalDataRepository
import org.braincards.app.domain.models.AuthState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Todos con runTest: el tiempo es virtual (delay no espera de verdad) y testScheduler.currentTime
 * mide cuanto "tarda" el use case.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignOutAndWipeUseCaseTest {

    private val userId = "user-1"
    private val authenticated = AuthState.Authenticated(userId = userId, email = "a@b.c", sessionConfirmed = true)

    private val log = mutableListOf<String>()
    private val auth = FakeAuthRepository(initialState = authenticated, callLog = log)
    private val local = FakeLocalDataRepository(callLog = log)
    private val useCase = SignOutAndWipeUseCase(auth, local)

    private suspend fun currentState() = auth.authState.first()

    @Test
    fun borra_primero_y_cierra_sesion_remota_despues_sin_usar_el_respaldo_local() = runTest {
        val result = useCase(userId)

        assertEquals(SignOutAndWipeResult.SignedOut, result)
        assertEquals(listOf("wipeAll", "signOut"), log)
        assertFalse(auth.clearLocalSessionCalled)
        assertEquals(AuthState.NotAuthenticated, currentState())
    }

    @Test
    fun si_signOut_lanza_una_excepcion_cierra_la_sesion_con_clearLocalSession() = runTest {
        auth.signOutFailure = RuntimeException("sin red")

        val result = useCase(userId)

        assertEquals(SignOutAndWipeResult.SignedOut, result)
        assertEquals(listOf("wipeAll", "signOut", "clearLocalSession"), log)
        assertEquals(AuthState.NotAuthenticated, currentState())
    }

    @Test
    fun si_signOut_no_responde_se_corta_al_limite_y_cierra_con_clearLocalSession() = runTest {
        auth.signOutDelay = 60.seconds

        val result = useCase(userId)

        assertEquals(SignOutAndWipeResult.SignedOut, result)
        // Tiempo virtual: espera exactamente el limite, no los 60 s del logout colgado.
        assertEquals(REMOTE_SIGN_OUT_TIMEOUT.inWholeMilliseconds, testScheduler.currentTime)
        assertEquals(listOf("wipeAll", "signOut", "clearLocalSession"), log)
        assertEquals(AuthState.NotAuthenticated, currentState())
    }

    @Test
    fun si_signOut_responde_justo_antes_del_limite_no_hace_falta_el_respaldo() = runTest {
        val almostTimeout = REMOTE_SIGN_OUT_TIMEOUT - 1.milliseconds
        auth.signOutDelay = almostTimeout

        val result = useCase(userId)

        assertEquals(SignOutAndWipeResult.SignedOut, result)
        assertEquals(almostTimeout.inWholeMilliseconds, testScheduler.currentTime)
        assertFalse(auth.clearLocalSessionCalled)
        assertEquals(AuthState.NotAuthenticated, currentState())
    }

    @Test
    fun si_wipeAll_falla_devuelve_error_y_no_cierra_la_sesion() = runTest {
        val boom = IllegalStateException("disco lleno")
        local.wipeFailure = boom

        val result = useCase(userId)

        val failed = assertIs<SignOutAndWipeResult.WipeFailed>(result)
        assertSame(boom, failed.cause)
        assertEquals(listOf("wipeAll"), log)
        assertFalse(auth.signOutCalled)
        assertFalse(auth.clearLocalSessionCalled)
        assertEquals(authenticated, currentState())
    }

    @Test
    fun si_el_usuario_actual_es_otro_no_borra_ni_cierra_nada() = runTest {
        auth.setState(AuthState.Authenticated(userId = "user-2", email = null, sessionConfirmed = true))

        val result = useCase(userId)

        assertEquals(SignOutAndWipeResult.SessionChanged, result)
        assertTrue(log.isEmpty())
        assertFalse(local.wipeCompleted)
    }

    @Test
    fun si_ya_no_hay_sesion_no_borra_ni_cierra_nada() = runTest {
        auth.setState(AuthState.NotAuthenticated)

        assertEquals(SignOutAndWipeResult.SessionChanged, useCase(userId))
        assertTrue(log.isEmpty())
    }

    @Test
    fun si_la_sesion_esta_cargando_no_borra_ni_cierra_nada() = runTest {
        auth.setState(AuthState.Loading)

        assertEquals(SignOutAndWipeResult.SessionChanged, useCase(userId))
        assertTrue(log.isEmpty())
    }

    @Test
    fun si_tambien_falla_clearLocalSession_devuelve_SessionCloseFailed_con_la_bd_ya_vaciada() = runTest {
        val boom = IllegalStateException("no se pudo borrar la sesion guardada")
        auth.signOutFailure = RuntimeException("sin red")
        auth.clearLocalSessionFailure = boom

        val result = useCase(userId)

        val failed = assertIs<SignOutAndWipeResult.SessionCloseFailed>(result)
        assertSame(boom, failed.cause)
        assertTrue(local.wipeCompleted)
        assertEquals(authenticated, currentState())
    }

    @Test
    fun cancelar_mientras_el_signOut_remoto_esta_en_curso_no_deja_la_sesion_viva() = runTest {
        auth.signOutDelay = 2.seconds

        val job = launch { useCase(userId) }
        advanceTimeBy(1.seconds) // wipeAll ya termino; signOut sigue esperando
        job.cancel()
        advanceUntilIdle()

        // Sin NonCancellable el delay del signOut se cancelaria y la sesion seguiria viva.
        assertEquals(AuthState.NotAuthenticated, currentState())
        assertEquals(listOf("wipeAll", "signOut"), log)
    }

    @Test
    fun cancelar_mientras_wipeAll_esta_en_curso_termina_el_borrado_y_cierra_la_sesion() = runTest {
        local.wipeDelay = 2.seconds

        val job = launch { useCase(userId) }
        advanceTimeBy(1.seconds) // wipeAll sigue en curso
        job.cancel()
        advanceUntilIdle()

        // Sin NonCancellable el borrado se cancelaria: ni se vaciaria la BD ni se cerraria la sesion.
        assertTrue(local.wipeCompleted)
        assertEquals(AuthState.NotAuthenticated, currentState())
    }
}
