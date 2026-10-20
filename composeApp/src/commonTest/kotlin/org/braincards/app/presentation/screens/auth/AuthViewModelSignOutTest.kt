package org.braincards.app.presentation.screens.auth

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeLocalDataRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.ConfirmPasswordResetUseCase
import org.braincards.app.domain.usecase.Auth.DeleteAccountUseCase
import org.braincards.app.domain.usecase.Auth.ResendSignUpCodeUseCase
import org.braincards.app.domain.usecase.Auth.SendPasswordResetUseCase
import org.braincards.app.domain.usecase.Auth.SignInUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.domain.usecase.Auth.SignUpUseCase
import org.braincards.app.domain.usecase.Auth.UpdatePasswordUseCase
import org.braincards.app.domain.usecase.Auth.VerifyPasswordResetCodeUseCase
import org.braincards.app.domain.usecase.Auth.VerifySignUpCodeUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import org.braincards.app.presentation.sync.SignOutPrompt
import org.braincards.app.presentation.sync.SyncCoordinator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cierre de sesion en AuthViewModel. Dispatchers.Main se sustituye por un dispatcher de test
 * (viewModelScope usa Main). Sesion NO confirmada (offline) en casi todos: el sync previo no se
 * intenta y el conteo es el local, asi el resultado es determinista.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelSignOutTest {

    private val userId = "user-1"
    private val testDispatcher = StandardTestDispatcher()
    private val themeKey = stringPreferencesKey("app_theme")
    private val lastSyncKey = stringPreferencesKey("last_successful_sync_at")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Env(scope: CoroutineScope, prefs: Preferences) {
        val auth = FakeAuthRepository(initialState = AuthState.Authenticated("user-1", "a@b.c", sessionConfirmed = false))
        val remote = FakeRemoteDataSource()
        val decks = FakeDecksRepository()
        val topics = FakeTopicsRepository()
        val flashcards = FakeFlashcardRepository()
        val scheduling = FakeCardSchedulingRepository()
        val local = FakeLocalDataRepository()
        val dataStore = FakeSettingsDataStore(prefs)
        val coordinator = SyncCoordinator(
            authRepository = auth,
            syncEngineUseCase = SyncEngineUseCase(
                auth, remote, decks, topics, flashcards, scheduling,
                BackfillLocalDataUseCase(decks, topics, flashcards, scheduling)
            ),
            decksRepository = decks,
            topicsRepository = topics,
            flashcardRepository = flashcards,
            cardSchedulingRepository = scheduling,
            dataStore = dataStore,
            localDataRepository = local,
            signOutAndWipeUseCase = SignOutAndWipeUseCase(auth, local),
            coroutineScope = scope
        )
        val vm = AuthViewModel(
            authRepository = auth,
            signUpUseCase = SignUpUseCase(auth),
            signInUseCase = SignInUseCase(auth),
            sendPasswordResetUseCase = SendPasswordResetUseCase(auth),
            confirmPasswordResetUseCase = ConfirmPasswordResetUseCase(
                VerifyPasswordResetCodeUseCase(auth), UpdatePasswordUseCase(auth)
            ),
            verifySignUpCodeUseCase = VerifySignUpCodeUseCase(auth),
            resendSignUpCodeUseCase = ResendSignUpCodeUseCase(auth),
            deleteAccountUseCase = DeleteAccountUseCase(auth, decks, topics, flashcards, scheduling),
            syncCoordinator = coordinator
        )
        val events = mutableListOf<SignOutEvent>()
    }

    private fun TestScope.env(prefs: Preferences = emptyPreferences()): Env {
        val env = Env(backgroundScope, prefs)
        // authState del ViewModel es WhileSubscribed: la pantalla lo recoge; aqui tambien.
        backgroundScope.launch { env.vm.authState.collect { } }
        // Unconfined: un SharedFlow sin buffer suspende emit() hasta que el colector recibe el valor;
        // asi el colector del test lo recibe en el acto y no depende del orden de la cola de test.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.signOutEvent.collect { env.events += it } }
        runCurrent()
        return env
    }

    private suspend fun Env.addPendingDeck(id: String) = decks.addDeck(
        Deck(id, id, Instant.fromEpochMilliseconds(1000), Instant.fromEpochMilliseconds(2000), null, userId = "user-1")
    )

    // --- sin nada que perder ---

    @Test
    fun sin_nada_que_perder_cierra_sesion_directamente_sin_dialogo() = runTest(testDispatcher) {
        val env = env()

        env.vm.requestSignOut()
        advanceUntilIdle()

        assertNull(env.vm.signOutPrompt.value)
        assertEquals(listOf(SignOutEvent.SignedOut), env.events)
        assertTrue(env.local.wipeCompleted)
        assertEquals(AuthState.NotAuthenticated, env.auth.authState.first())
        assertFalse(env.vm.isSigningOut.value)
    }

    // --- con cambios sin sincronizar: dialogo ---

    @Test
    fun con_cambios_sin_sincronizar_abre_el_dialogo_con_el_numero_y_no_borra_nada() = runTest(testDispatcher) {
        val env = env()
        env.addPendingDeck("d1")

        env.vm.requestSignOut()
        advanceUntilIdle()

        assertEquals(SignOutPrompt.NeedsConfirmation(1), env.vm.signOutPrompt.value)
        assertFalse(env.vm.isSigningOut.value, "con el dialogo esperando decision, los botones deben estar activos")
        assertEquals(0, env.local.wipeCallCount)
        assertTrue(env.events.isEmpty())
    }

    @Test
    fun cancelar_el_dialogo_no_borra_nada_ni_cierra_la_sesion() = runTest(testDispatcher) {
        val env = env()
        env.addPendingDeck("d1")
        env.vm.requestSignOut()
        advanceUntilIdle()

        env.vm.dismissSignOut()
        advanceUntilIdle()

        assertNull(env.vm.signOutPrompt.value)
        assertEquals(0, env.local.wipeCallCount)
        assertFalse(env.auth.signOutCalled)
        assertFalse(env.auth.clearLocalSessionCalled)
        assertTrue(env.events.isEmpty())
        assertEquals(AuthState.Authenticated("user-1", "a@b.c", false), env.auth.authState.first())
    }

    // --- resultados ---

    @Test
    fun confirmar_cierra_la_sesion_y_emite_SignedOut() = runTest(testDispatcher) {
        val env = env()
        env.addPendingDeck("d1")
        env.vm.requestSignOut()
        advanceUntilIdle()

        env.vm.confirmSignOut()
        advanceUntilIdle()

        assertEquals(listOf(SignOutEvent.SignedOut), env.events)
        assertNull(env.vm.signOutPrompt.value)
        assertTrue(env.local.wipeCompleted)
        assertEquals(AuthState.NotAuthenticated, env.auth.authState.first())
        assertFalse(env.vm.isSigningOut.value)
    }

    @Test
    fun si_falla_el_borrado_emite_WipeFailed_y_la_sesion_sigue_viva() = runTest(testDispatcher) {
        val env = env()
        env.addPendingDeck("d1")
        env.local.wipeFailure = IllegalStateException("disco lleno")
        env.vm.requestSignOut()
        advanceUntilIdle()

        env.vm.confirmSignOut()
        advanceUntilIdle()

        assertEquals(listOf(SignOutEvent.WipeFailed), env.events)
        assertNull(env.vm.signOutPrompt.value)
        assertFalse(env.auth.signOutCalled)
        assertEquals(AuthState.Authenticated("user-1", "a@b.c", false), env.auth.authState.first())
        assertFalse(env.vm.isSigningOut.value)
    }

    @Test
    fun si_no_se_puede_cerrar_la_sesion_emite_SessionCloseFailed() = runTest(testDispatcher) {
        val env = env()
        env.auth.signOutFailure = RuntimeException("sin red")
        env.auth.clearLocalSessionFailure = IllegalStateException("no se pudo")

        env.vm.requestSignOut() // sin nada que perder: cierra directamente
        advanceUntilIdle()

        assertEquals(listOf(SignOutEvent.SessionCloseFailed), env.events)
        assertTrue(env.local.wipeCompleted)
        assertFalse(env.vm.isSigningOut.value)
    }

    @Test
    fun si_la_sesion_cambio_con_el_dialogo_abierto_emite_SessionChanged_y_no_borra() = runTest(testDispatcher) {
        val env = env()
        env.addPendingDeck("d1")
        env.vm.requestSignOut()
        advanceUntilIdle()
        env.auth.setState(AuthState.Authenticated("otro-usuario", null, sessionConfirmed = false))

        env.vm.confirmSignOut()
        advanceUntilIdle()

        assertEquals(listOf(SignOutEvent.SessionChanged), env.events)
        assertEquals(0, env.local.wipeCallCount)
        assertNull(env.vm.signOutPrompt.value)
    }

    @Test
    fun si_entraron_mas_cambios_con_el_dialogo_abierto_lo_vuelve_a_mostrar_con_el_numero_nuevo() = runTest(testDispatcher) {
        val env = env()
        env.addPendingDeck("d1")
        env.vm.requestSignOut()
        advanceUntilIdle()
        assertEquals(SignOutPrompt.NeedsConfirmation(1), env.vm.signOutPrompt.value)

        env.addPendingDeck("d2") // el usuario edita con el dialogo abierto
        env.vm.confirmSignOut()
        advanceUntilIdle()

        assertEquals(SignOutPrompt.NeedsConfirmation(2), env.vm.signOutPrompt.value)
        assertTrue(env.events.isEmpty())
        assertEquals(0, env.local.wipeCallCount)
        assertFalse(env.vm.isSigningOut.value)

        env.vm.confirmSignOut() // ahora confirma con el numero nuevo
        advanceUntilIdle()
        assertEquals(listOf(SignOutEvent.SignedOut), env.events)
    }

    // --- rotacion: el estado vive en el ViewModel ---

    @Test
    fun el_dialogo_y_el_progreso_se_conservan_en_el_viewmodel_para_un_colector_nuevo_tras_rotar() = runTest(testDispatcher) {
        val env = env()
        env.addPendingDeck("d1")
        env.vm.requestSignOut()
        advanceUntilIdle()

        // Rotacion: el Composable se destruye y uno nuevo recoge los StateFlow del mismo ViewModel.
        assertEquals(SignOutPrompt.NeedsConfirmation(1), env.vm.signOutPrompt.first())

        val gate = CompletableDeferred<Unit>()
        env.auth.signOutGate = gate
        env.vm.confirmSignOut()
        runCurrent() // el borrado se hizo y el signOut esta parado en la compuerta

        // Otra "rotacion" a mitad del borrado: sigue el progreso y el dialogo.
        assertTrue(env.vm.isSigningOut.first())
        assertNotNull(env.vm.signOutPrompt.first())

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(env.vm.isSigningOut.first())
        assertNull(env.vm.signOutPrompt.first())
    }

    // --- doble pulsacion y dialogo bloqueado a mitad del borrado ---

    @Test
    fun pulsar_cerrar_sesion_dos_veces_solo_lo_ejecuta_una() = runTest(testDispatcher) {
        val env = env()

        env.vm.requestSignOut()
        env.vm.requestSignOut()
        advanceUntilIdle()

        assertEquals(1, env.local.wipeCallCount)
        assertEquals(listOf(SignOutEvent.SignedOut), env.events)
    }

    @Test
    fun pulsar_confirmar_dos_veces_solo_borra_una_y_dismiss_se_ignora_a_mitad_del_borrado() = runTest(testDispatcher) {
        val env = env()
        env.addPendingDeck("d1")
        env.vm.requestSignOut()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        env.auth.signOutGate = gate

        env.vm.confirmSignOut()
        runCurrent() // borrado hecho, signOut parado en la compuerta
        env.vm.confirmSignOut() // segunda pulsacion a mitad: ignorada
        env.vm.dismissSignOut() // "atras"/Cancelar a mitad: ignorado
        runCurrent()

        assertNotNull(env.vm.signOutPrompt.value, "el dialogo no se puede cerrar a mitad del borrado")
        assertTrue(env.vm.isSigningOut.value)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, env.local.wipeCallCount)
        assertEquals(listOf(SignOutEvent.SignedOut), env.events)
    }

    // --- borrado de cuenta ---

    @Test
    fun al_borrar_la_cuenta_se_resetea_el_estado_de_sync_pero_se_conserva_el_tema() = runTest(testDispatcher) {
        val env = env(preferencesOf(themeKey to "DARK", lastSyncKey to "2026-01-01T00:00:00Z"))
        runCurrent()
        assertNotNull(env.coordinator.lastSuccessfulSyncAt.value)

        env.vm.deleteAccount()
        advanceUntilIdle()

        assertTrue(env.auth.deleteAccountCalled)
        assertNull(env.coordinator.lastSuccessfulSyncAt.value)
        val prefs = env.dataStore.data.first()
        assertNull(prefs[lastSyncKey])
        assertEquals("DARK", prefs[themeKey])
        assertFalse(env.vm.isDeletingAccount.value)
    }

    @Test
    fun si_falla_el_borrado_de_cuenta_remoto_no_se_resetea_el_estado_de_sync() = runTest(testDispatcher) {
        val env = env(preferencesOf(lastSyncKey to "2026-01-01T00:00:00Z"))
        runCurrent()
        env.auth.deleteAccountFailure = RuntimeException("sin red")

        env.vm.deleteAccount()
        advanceUntilIdle()

        assertNotNull(env.coordinator.lastSuccessfulSyncAt.value)
        assertEquals("2026-01-01T00:00:00Z", env.dataStore.data.first()[lastSyncKey])
    }
}
