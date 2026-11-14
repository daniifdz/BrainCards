package org.braincards.app.presentation.screens.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeLocalDataRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.AuthState
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
import org.braincards.app.presentation.sync.SyncCoordinator

/**
 * AuthViewModel REAL con repositorios falsos (el constructor no cambia). Requiere Dispatchers.setMain en el test
 * (viewModelScope). Compartido entre commonTest y jvmTest.
 */
internal class AuthVmEnv(initial: AuthState = AuthState.NotAuthenticated) {
    val auth = FakeAuthRepository(initial)
    private val remote = FakeRemoteDataSource()
    private val decks = FakeDecksRepository()
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
        coroutineScope = CoroutineScope(Dispatchers.Unconfined)
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

    init {
        // authState del ViewModel es WhileSubscribed: la pantalla lo recoge; aqui tambien (si no, vale Loading).
        CoroutineScope(Dispatchers.Unconfined).launch { vm.authState.collect { } }
    }
}
