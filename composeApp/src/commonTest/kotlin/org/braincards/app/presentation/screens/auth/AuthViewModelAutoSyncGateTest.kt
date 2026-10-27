package org.braincards.app.presentation.screens.auth

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.braincards.app.presentation.sync.AutoSyncOutcome
import org.braincards.app.presentation.sync.AutoSyncPauseGate
import org.braincards.app.presentation.sync.AutoSyncSkipReason
import org.braincards.app.presentation.sync.PauseReason
import org.braincards.app.presentation.sync.SignOutPrompt
import org.braincards.app.presentation.sync.SyncCoordinator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * La pausa de la sync automatica durante el cierre de sesion no puede quedarse puesta si el dialogo se cierra
 * sin pasar por dismissSignOut (pantalla destruida con el dialogo abierto).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelAutoSyncGateTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Env {
        val gate = AutoSyncPauseGate()
        val auth = FakeAuthRepository(AuthState.Authenticated("user-1", "a@b.c", sessionConfirmed = false))
        val remote = FakeRemoteDataSource()
        val decks = FakeDecksRepository()
        private val topics = FakeTopicsRepository()
        private val flashcards = FakeFlashcardRepository()
        private val scheduling = FakeCardSchedulingRepository()
        private val local = FakeLocalDataRepository()
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
            dataStore = FakeSettingsDataStore(),
            localDataRepository = local,
            signOutAndWipeUseCase = SignOutAndWipeUseCase(auth, local),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            pauseGate = gate
        )
        private val store = ViewModelStore()
        val vm: AuthViewModel = ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer {
                    AuthViewModel(
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
                }
            }
        )[AuthViewModel::class]

        // authState del ViewModel es WhileSubscribed: la pantalla lo recoge; aqui tambien (si no, vale Loading).
        init {
            CoroutineScope(Dispatchers.Unconfined).launch { vm.authState.collect { } }
        }

        /** Destruye el ViewModel como lo hace la navegacion al salir de la pantalla: invoca onCleared. */
        fun clear() = store.clear()

        suspend fun openDialog() {
            decks.addDeck(Deck("d1", "d1", Instant.fromEpochMilliseconds(1000), Instant.fromEpochMilliseconds(2000), null, userId = "user-1"))
            vm.requestSignOut()
            assertEquals(SignOutPrompt.NeedsConfirmation(1), vm.signOutPrompt.value)
        }
    }

    @Test
    fun con_el_dialogo_abierto_la_sync_automatica_esta_en_pausa() = runBlocking<Unit> {
        val env = Env()

        env.openDialog()

        assertEquals(PauseReason.SIGN_OUT, env.gate.activeReason())
    }

    @Test
    fun cancelar_el_dialogo_reanuda_la_sync_automatica() = runBlocking<Unit> {
        val env = Env()
        env.openDialog()

        env.vm.dismissSignOut()

        assertNull(env.gate.activeReason())
    }

    @Test
    fun cerrar_el_dialogo_sin_dismissSignOut_no_deja_la_sync_desactivada_onCleared() = runBlocking<Unit> {
        val env = Env()
        env.openDialog()
        assertEquals(PauseReason.SIGN_OUT, env.gate.activeReason())

        env.clear() // la pantalla se destruye con el dialogo abierto, sin cancelar

        assertNull(env.gate.activeReason())
    }

    @Test
    fun tras_destruir_el_viewmodel_con_el_dialogo_abierto_una_sync_automatica_se_ejecuta() = runBlocking<Unit> {
        val env = Env()
        env.auth.setState(AuthState.Authenticated("user-1", "a@b.c", sessionConfirmed = true))
        env.openDialogConfirmed()

        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_SIGN_OUT), env.coordinator.requestAutoSync())
        env.clear()

        assertIs<AutoSyncOutcome.Ran>(env.coordinator.requestAutoSync())
    }

    @Test
    fun destruir_un_viewmodel_sin_cierre_en_curso_no_afecta_a_otras_pausas() = runBlocking<Unit> {
        val env = Env()
        val study = env.gate.acquire(PauseReason.STUDY)

        env.clear()

        assertEquals(PauseReason.STUDY, env.gate.activeReason())
        study.release()
    }

    private suspend fun Env.openDialogConfirmed() {
        // El push del sync previo del cierre falla, asi que el cambio sigue pendiente y se abre el dialogo.
        decks.addDeck(Deck("d1", "d1", Instant.fromEpochMilliseconds(1000), Instant.fromEpochMilliseconds(2000), null, userId = "user-1"))
        remote.pushDeckIdsThatFail = setOf("d1")
        vm.requestSignOut()
        assertEquals(SignOutPrompt.NeedsConfirmation(1), vm.signOutPrompt.value)
    }
}
