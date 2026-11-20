package org.braincards.app.domain.usecase.Sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
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
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.presentation.sync.AutoSyncOutcome
import org.braincards.app.presentation.sync.AutoSyncPauseGate
import org.braincards.app.presentation.sync.AutoSyncSkipReason
import org.braincards.app.presentation.sync.FinishSignOutResult
import org.braincards.app.presentation.sync.PauseReason
import org.braincards.app.presentation.sync.SIGN_OUT_PAUSE_TTL
import org.braincards.app.presentation.sync.SignOutPrompt
import org.braincards.app.presentation.sync.SyncCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * SyncCoordinator.requestAutoSync(): sesion y puerta de pausa comprobadas DENTRO del Mutex, sin solaparse con un
 * sync manual ni con el cierre de sesion; y lastOutcome. Reloj monotonico y reloj de pared falsos.
 */
class SyncCoordinatorAutoSyncTest {

    private val userId = "user-1"
    private val fixedNow = Instant.parse("2026-10-28T10:00:00Z")

    private class FixedClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class Fixture(
        userId: String,
        authState: AuthState? = null,
        pullDelayMillis: Long = 0,
        decksOverride: ((FakeDecksRepository) -> DecksRepository)? = null,
        fixedNow: Instant
    ) {
        val time = TestTimeSource()
        val gate = AutoSyncPauseGate(timeSource = time)
        val clock = FixedClock(fixedNow)
        val auth = FakeAuthRepository(authState ?: AuthState.Authenticated(userId, "a@b.com", sessionConfirmed = true))
        val remote = FakeRemoteDataSource().apply { pullDecksDelayMillis = pullDelayMillis }
        val fakeDecks = FakeDecksRepository()
        val decks: DecksRepository = decksOverride?.invoke(fakeDecks) ?: fakeDecks
        val topics = FakeTopicsRepository()
        val flashcards = FakeFlashcardRepository()
        val scheduling = FakeCardSchedulingRepository()
        val localData = FakeLocalDataRepository()
        val coordinator = SyncCoordinator(
            authRepository = auth,
            syncEngineUseCase = SyncEngineUseCase(
                authRepository = auth,
                remoteDataSource = remote,
                decksRepository = decks,
                topicsRepository = topics,
                flashcardRepository = flashcards,
                cardSchedulingRepository = scheduling,
                backfillLocalDataUseCase = BackfillLocalDataUseCase(decks, topics, flashcards, scheduling)
            ),
            decksRepository = decks,
            topicsRepository = topics,
            flashcardRepository = flashcards,
            cardSchedulingRepository = scheduling,
            dataStore = FakeSettingsDataStore(),
            localDataRepository = localData,
            signOutAndWipeUseCase = SignOutAndWipeUseCase(auth, localData),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            pauseGate = gate,
            clock = clock
        )
        /** Peticiones de pull de decks hechas hasta ahora (una por sync que llega al motor). */
        val pulls get() = remote.calls.count { it.startsWith("pullDecks") }
    }

    private fun fixture(
        authState: AuthState? = null,
        pullDelayMillis: Long = 0,
        decksOverride: ((FakeDecksRepository) -> DecksRepository)? = null
    ) = Fixture(userId, authState, pullDelayMillis, decksOverride, fixedNow)

    private suspend fun <T> awaitValue(read: () -> T, expected: T) {
        withTimeout(5_000) { while (read() != expected) delay(5) }
    }

    private fun pendingDeck(id: String) =
        Deck(id, id, Instant.fromEpochMilliseconds(1000), Instant.fromEpochMilliseconds(2000), null, userId = "user-1")

    // --- omitido, con su motivo ---

    @Test
    fun sin_sesion_se_omite_con_NO_SESSION() = runBlocking<Unit> {
        val f = fixture(authState = AuthState.NotAuthenticated)

        val outcome = f.coordinator.requestAutoSync()

        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.NO_SESSION), outcome)
        assertTrue(f.remote.calls.isEmpty())
    }

    @Test
    fun con_la_sesion_aun_cargando_se_omite_con_NO_SESSION() = runBlocking<Unit> {
        val f = fixture(authState = AuthState.Loading)

        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.NO_SESSION), f.coordinator.requestAutoSync())
    }

    @Test
    fun con_sesion_sin_confirmar_se_omite_con_SESSION_UNCONFIRMED() = runBlocking<Unit> {
        val f = fixture(authState = AuthState.Authenticated(userId, "a@b.com", sessionConfirmed = false))

        val outcome = f.coordinator.requestAutoSync()

        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.SESSION_UNCONFIRMED), outcome)
        assertTrue(f.remote.calls.isEmpty())
    }

    @Test
    fun con_una_sesion_de_estudio_activa_se_omite_con_PAUSED_STUDY() = runBlocking<Unit> {
        val f = fixture()
        val pullsBefore = f.pulls
        f.gate.acquire(PauseReason.STUDY)

        val outcome = f.coordinator.requestAutoSync()

        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_STUDY), outcome)
        assertEquals(pullsBefore, f.pulls)
    }

    @Test
    fun con_estudio_y_cierre_a_la_vez_el_motivo_es_PAUSED_SIGN_OUT() = runBlocking<Unit> {
        val f = fixture()
        f.gate.acquire(PauseReason.STUDY)
        f.gate.acquire(PauseReason.SIGN_OUT)

        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_SIGN_OUT), f.coordinator.requestAutoSync())
    }

    @Test
    fun con_sesion_y_puerta_abierta_sincroniza() = runBlocking<Unit> {
        val f = fixture()
        val pullsBefore = f.pulls

        val outcome = f.coordinator.requestAutoSync()

        assertIs<SyncResult.Completed>(assertIs<AutoSyncOutcome.Ran>(outcome).result)
        assertEquals(pullsBefore + 1, f.pulls)
    }

    @Test
    fun al_liberar_la_puerta_de_estudio_vuelve_a_sincronizar() = runBlocking<Unit> {
        val f = fixture()
        val lease = f.gate.acquire(PauseReason.STUDY)
        assertIs<AutoSyncOutcome.Skipped>(f.coordinator.requestAutoSync())

        lease.release()

        assertIs<AutoSyncOutcome.Ran>(f.coordinator.requestAutoSync())
    }

    // --- sesion y puerta se comprueban DENTRO del lock ---

    @Test
    fun si_la_sesion_se_cierra_mientras_espera_el_lock_se_omite_y_no_toca_la_red() = runBlocking<Unit> {
        val f = fixture()
        val pullsBefore = f.pulls
        val inLock = CompletableDeferred<Unit>()
        val letGo = CompletableDeferred<Unit>()
        val holder = launch { f.coordinator.withSyncLock { inLock.complete(Unit); letGo.await() } }
        inLock.await()
        val auto = async { f.coordinator.requestAutoSync() }
        repeat(3) { kotlinx.coroutines.yield() } // la peticion ya esta encolada detras del lock

        f.auth.setState(AuthState.NotAuthenticated)
        letGo.complete(Unit)
        holder.join()

        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.NO_SESSION), auto.await())
        assertEquals(pullsBefore, f.pulls)
    }

    @Test
    fun si_empieza_un_estudio_mientras_espera_el_lock_se_omite() = runBlocking<Unit> {
        val f = fixture()
        val pullsBefore = f.pulls
        val inLock = CompletableDeferred<Unit>()
        val letGo = CompletableDeferred<Unit>()
        val holder = launch { f.coordinator.withSyncLock { inLock.complete(Unit); letGo.await() } }
        inLock.await()
        val auto = async { f.coordinator.requestAutoSync() }
        repeat(3) { kotlinx.coroutines.yield() }

        f.gate.acquire(PauseReason.STUDY)
        letGo.complete(Unit)
        holder.join()

        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_STUDY), auto.await())
        assertEquals(pullsBefore, f.pulls)
    }

    // --- no se solapa ---

    @Test
    fun no_se_solapa_con_un_sync_manual() = runBlocking<Unit> {
        val f = fixture(pullDelayMillis = 50)

        listOf(
            async { f.coordinator.syncNow() },
            async { f.coordinator.requestAutoSync() },
            async { f.coordinator.requestAutoSync() },
            async { f.coordinator.syncNow() }
        ).awaitAll()

        assertEquals(1, f.remote.maxConcurrentPullDecksCalls)
    }

    @Test
    fun el_sync_manual_no_se_bloquea_con_la_puerta_cerrada() = runBlocking<Unit> {
        val f = fixture()
        f.gate.acquire(PauseReason.STUDY)
        f.gate.acquire(PauseReason.SIGN_OUT)
        val pullsBefore = f.pulls

        val result = f.coordinator.syncNow()

        assertIs<SyncResult.Completed>(result)
        assertEquals(pullsBefore + 1, f.pulls)
    }

    @Test
    fun un_sync_automatico_encolado_detras_de_un_sync_en_curso_no_se_cruza_con_el_cierre() = runBlocking<Unit> {
        val f = fixture(pullDelayMillis = 60)
        val manual = async { f.coordinator.syncNow() }          // tiene el Mutex
        awaitValue({ f.remote.maxConcurrentPullDecksCalls }, 1)
        val auto = async { f.coordinator.requestAutoSync() }
        repeat(3) { kotlinx.coroutines.yield() }

        val prompt = f.coordinator.prepareSignOut()              // cierra la puerta ANTES de esperar el lock

        manual.await()
        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_SIGN_OUT), auto.await())
        assertEquals(SignOutPrompt.ReadyToSignOut, prompt)
    }

    // --- ciclo de vida del lease del cierre de sesion ---

    @Test
    fun entre_prepareSignOut_y_abortSignOut_no_hay_sync_automatica_y_luego_si() = runBlocking<Unit> {
        val f = fixture()
        f.coordinator.prepareSignOut()

        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_SIGN_OUT), f.coordinator.requestAutoSync())

        f.coordinator.abortSignOut()

        assertIs<AutoSyncOutcome.Ran>(f.coordinator.requestAutoSync())
    }

    @Test
    fun abortSignOut_sin_cierre_en_curso_no_hace_nada() = runBlocking<Unit> {
        val f = fixture()

        f.coordinator.abortSignOut()
        f.coordinator.abortSignOut()

        assertIs<AutoSyncOutcome.Ran>(f.coordinator.requestAutoSync())
    }

    @Test
    fun finishSignOut_terminado_libera_la_puerta() = runBlocking<Unit> {
        val f = fixture()
        f.coordinator.prepareSignOut()
        assertEquals(1, f.gate.activeCount(PauseReason.SIGN_OUT))

        val result = f.coordinator.finishSignOut(userId, confirmedCount = 0)

        assertIs<FinishSignOutResult.Finished>(result)
        assertEquals(0, f.gate.activeCount(PauseReason.SIGN_OUT))
    }

    @Test
    fun tras_el_cierre_la_sync_automatica_ve_que_no_hay_sesion_y_no_repuebla() = runBlocking<Unit> {
        val f = fixture()
        f.coordinator.prepareSignOut()
        f.coordinator.finishSignOut(userId, confirmedCount = 0)
        val pullsBefore = f.pulls

        val outcome = f.coordinator.requestAutoSync()

        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.NO_SESSION), outcome)
        assertEquals(pullsBefore, f.pulls)
    }

    @Test
    fun con_NeedsReconfirmation_el_dialogo_sigue_abierto_y_la_puerta_tambien() = runBlocking<Unit> {
        val f = fixture()
        f.remote.pushDeckIdsThatFail = setOf("d1", "d2")        // el push falla: siguen pendientes
        f.fakeDecks.addDeck(pendingDeck("d1"))
        val prompt = f.coordinator.prepareSignOut()
        assertEquals(SignOutPrompt.NeedsConfirmation(1), prompt)
        f.fakeDecks.addDeck(pendingDeck("d2"))                   // entra otro cambio mientras ve el dialogo

        val result = f.coordinator.finishSignOut(userId, confirmedCount = 1)

        assertEquals(FinishSignOutResult.NeedsReconfirmation(2), result)
        assertEquals(1, f.gate.activeCount(PauseReason.SIGN_OUT))
        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_SIGN_OUT), f.coordinator.requestAutoSync())
    }

    @Test
    fun si_prepareSignOut_falla_no_deja_la_puerta_cerrada() = runBlocking<Unit> {
        // Solo falla DESPUES de construir el coordinador: su init tambien cuenta pendientes y una excepcion alli
        // quedaria sin capturar y se filtraria a otro test.
        var failCounting = false
        val f = fixture(decksOverride = { real ->
            object : DecksRepository by real {
                override suspend fun getPendingSync(userId: String): List<Deck> =
                    if (failCounting) error("fallo simulado al contar") else real.getPendingSync(userId)
            }
        })
        failCounting = true

        val failure = runCatching { f.coordinator.prepareSignOut() }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(0, f.gate.activeCount(PauseReason.SIGN_OUT))
    }

    @Test
    fun un_cierre_abandonado_sin_abort_deja_de_pausar_al_caducar_su_plazo() = runBlocking<Unit> {
        val f = fixture()
        f.coordinator.prepareSignOut()
        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.PAUSED_SIGN_OUT), f.coordinator.requestAutoSync())

        f.time += SIGN_OUT_PAUSE_TTL + 1.seconds

        assertIs<AutoSyncOutcome.Ran>(f.coordinator.requestAutoSync())
    }

    // --- lastOutcome ---

    @Test
    fun lastOutcome_lo_escribe_cualquier_sync_manual_completo_o_automatico() = runBlocking<Unit> {
        val f = fixture()
        f.coordinator.resetSyncState()
        assertNull(f.coordinator.lastOutcome.value)

        f.coordinator.syncNow()
        val manual = f.coordinator.lastOutcome.value
        assertEquals(fixedNow, manual?.at)
        assertIs<SyncResult.Completed>(manual?.result)

        f.coordinator.resetSyncState()
        f.coordinator.requestFullPull()
        assertIs<SyncResult.Completed>(f.coordinator.lastOutcome.value?.result)

        f.coordinator.resetSyncState()
        f.coordinator.requestAutoSync()
        assertIs<SyncResult.Completed>(f.coordinator.lastOutcome.value?.result)
    }

    @Test
    fun un_sync_omitido_no_toca_lastOutcome() = runBlocking<Unit> {
        val f = fixture()
        f.coordinator.syncNow()
        val before = f.coordinator.lastOutcome.value
        f.clock.now = Instant.parse("2026-10-28T11:00:00Z")
        f.gate.acquire(PauseReason.STUDY)

        f.coordinator.requestAutoSync()

        assertEquals(before, f.coordinator.lastOutcome.value)
    }

    @Test
    fun resetSyncState_borra_lastOutcome() = runBlocking<Unit> {
        val f = fixture()
        f.coordinator.syncNow()
        assertNotNull(f.coordinator.lastOutcome.value)

        f.coordinator.resetSyncState()

        assertNull(f.coordinator.lastOutcome.value)
    }

}
