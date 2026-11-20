package org.braincards.app.presentation.sync

import org.braincards.app.data.remote.SupabaseFailureClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeLocalDataRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.PendingStamp
import org.braincards.app.domain.repository.PendingChangesObserver
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * AutoSyncController + SyncCoordinator REAL (con fakes de repositorios y remoto): sincroniza al arrancar y al iniciar
 * sesion (el coordinador ya no lo hace en su init), no se solapa con un sync manual y respeta el cierre de sesion.
 * Tiempo virtual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoSyncControllerCoordinatorTest {

    private val userId = "user-1"

    private class FakePending : PendingChangesObserver {
        val stamp = MutableStateFlow(PendingStamp(0, 0))
        override fun observe(userId: String): StateFlow<PendingStamp> = stamp
    }

    private class Env(
        val test: TestScope,
        userId: String,
        initialAuth: AuthState = AuthState.Authenticated(userId, "a@b.c", sessionConfirmed = true)
    ) {
        val auth = FakeAuthRepository(initialAuth)
        val remote = FakeRemoteDataSource().apply { pullDecksDelayMillis = 200 }
        private val decks = FakeDecksRepository()
        private val topics = FakeTopicsRepository()
        private val flashcards = FakeFlashcardRepository()
        private val scheduling = FakeCardSchedulingRepository()
        private val local = FakeLocalDataRepository()
        val gate = AutoSyncPauseGate(timeSource = test.testTimeSource)
        val pending = FakePending()
        val coordinator = SyncCoordinator(
            authRepository = auth,
            syncEngineUseCase = SyncEngineUseCase(
                auth, remote, decks, topics, flashcards, scheduling,
                BackfillLocalDataUseCase(decks, topics, flashcards, scheduling),
                failureClassifier = SupabaseFailureClassifier
            ),
            decksRepository = decks,
            topicsRepository = topics,
            flashcardRepository = flashcards,
            cardSchedulingRepository = scheduling,
            dataStore = FakeSettingsDataStore(),
            localDataRepository = local,
            signOutAndWipeUseCase = SignOutAndWipeUseCase(auth, local),
            coroutineScope = test.backgroundScope,
            pauseGate = gate
        )
        val controller = AutoSyncController(
            scope = test.backgroundScope,
            timeSource = test.testTimeSource,
            random = object : Random() {
                override fun nextBits(bitCount: Int): Int = 0
                override fun nextDouble(): Double = 0.5
            },
            host = coordinator,
            authRepository = auth,
            pendingObserver = pending,
            pauseGate = gate,
            foregroundMonitor = object : ForegroundMonitor { override val isForeground = MutableStateFlow(true) },
            connectivityMonitor = object : ConnectivityMonitor { override val isOnline = MutableStateFlow(true) }
        )

        val pulls get() = remote.calls.count { it == "pullDecks" }

        fun advance(d: Duration) {
            test.advanceTimeBy(d)
            test.runCurrent()
        }
    }

    @Test
    fun no_se_solapa_con_un_sync_manual_concurrente() = runTest {
        val env = Env(this, userId)
        env.controller.start()
        val manual = backgroundScope.launch { env.coordinator.syncNow() }
        runCurrent()

        env.advance(5.seconds)

        assertEquals(1, env.remote.maxConcurrentPullDecksCalls, "nunca dos pulls a la vez")
        assertTrue(env.pulls >= 2, "al menos el manual y el automatico")
        manual.join()
    }

    @Test
    fun el_arranque_con_sesion_confirmada_sincroniza_una_sola_vez_por_el_controlador() = runTest {
        val env = Env(this, userId)

        env.controller.start()
        env.advance(5.seconds)

        assertEquals(1, env.pulls, "un solo sync de arranque (primer plano + sesion confirmada + red), y es del controlador")
    }

    @Test
    fun un_inicio_de_sesion_nuevo_dispara_una_sync_por_el_controlador() = runTest {
        val env = Env(this, userId, initialAuth = AuthState.NotAuthenticated)
        env.controller.start()
        env.advance(10.seconds)
        assertEquals(0, env.pulls, "sin sesion no se sincroniza")

        env.auth.setState(AuthState.Authenticated(userId, "a@b.c", sessionConfirmed = true))
        env.advance(5.seconds)

        assertEquals(1, env.pulls)
    }

    @Test
    fun una_sesion_guardada_sin_confirmar_sincroniza_al_confirmarse() = runTest {
        val env = Env(this, userId, initialAuth = AuthState.Authenticated(userId, "a@b.c", sessionConfirmed = false))
        env.controller.start()
        env.advance(10.seconds)
        assertEquals(0, env.pulls, "arranque en frio sin red: aun no se habla con el servidor")

        env.auth.setState(AuthState.Authenticated(userId, "a@b.c", sessionConfirmed = true))
        env.advance(5.seconds)

        assertEquals(1, env.pulls)
    }

    @Test
    fun durante_el_cierre_de_sesion_no_hay_sync_automatica_y_al_cancelar_se_reanuda_con_uno_solo() = runTest {
        val env = Env(this, userId)
        env.controller.start()
        env.advance(40.seconds)
        env.coordinator.prepareSignOut()          // cierra la puerta y hace su propio sync previo
        val baseline = env.pulls

        env.pending.stamp.value = PendingStamp(1, 100)   // entra un cambio con el dialogo abierto
        env.advance(2.minutes)
        assertEquals(baseline, env.pulls, "ninguna sync automatica mientras el cierre sigue en curso")

        env.coordinator.abortSignOut()
        env.advance(5.seconds)

        assertEquals(baseline + 1, env.pulls, "al cancelar, un solo sync")
    }

    @Test
    fun tras_cerrar_la_sesion_el_controlador_no_sincroniza_ni_repuebla() = runTest {
        val env = Env(this, userId)
        env.controller.start()
        env.advance(40.seconds)
        env.coordinator.prepareSignOut()
        val result = env.coordinator.finishSignOut(userId, confirmedCount = 0)
        assertIs<FinishSignOutResult.Finished>(result)
        val baseline = env.pulls

        env.pending.stamp.value = PendingStamp(1, 100)
        env.advance(20.minutes)

        assertEquals(baseline, env.pulls)
    }

    @Test
    fun el_estudio_pausa_el_sync_automatico_y_al_terminar_lanza_uno() = runTest {
        val env = Env(this, userId)
        env.controller.start()
        env.advance(40.seconds)
        val baseline = env.pulls
        val study = env.gate.acquire(PauseReason.STUDY)

        env.pending.stamp.value = PendingStamp(3, 900)
        env.advance(3.minutes)
        assertEquals(baseline, env.pulls, "durante el estudio no se sincroniza solo")

        study.release()
        env.advance(5.seconds)

        assertEquals(baseline + 1, env.pulls)
    }
}
