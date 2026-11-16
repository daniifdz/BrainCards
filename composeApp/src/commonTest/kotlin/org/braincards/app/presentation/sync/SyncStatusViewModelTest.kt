package org.braincards.app.presentation.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.data.local.LAST_SUCCESSFUL_SYNC_KEY
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
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class FakeConnectivityMonitor(initial: Boolean = true) : ConnectivityMonitor {
    val online = MutableStateFlow(initial)
    override val isOnline = online
}

/** DataStore cuya primera lectura espera a [gate]: simula la ventana de arranque en la que la lectura no ha terminado. */
internal class GatedDataStore(
    private val inner: DataStore<Preferences>,
    val gate: CompletableDeferred<Unit> = CompletableDeferred(),
    private val failRead: Boolean = false
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow {
        gate.await()
        if (failRead) throw IllegalStateException("lectura fallida")
        emitAll(inner.data)
    }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        inner.updateData(transform)
}

/** Indicador de sync del ViewModel: auth estable, conectividad, y la lectura de la ultima sync correcta. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val last = Instant.parse("2026-10-27T10:00:00Z")
    private val signedIn = AuthState.Authenticated("u1", "a@b.c", sessionConfirmed = true)

    private class Env(
        initialAuth: AuthState,
        online: Boolean = true,
        dataStore: DataStore<Preferences> = FakeSettingsDataStore(),
        timeSource: TimeSource = TimeSource.Monotonic
    ) {
        val auth = FakeAuthRepository(initialAuth)
        val connectivity = FakeConnectivityMonitor(online)
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
            dataStore = dataStore,
            localDataRepository = local,
            signOutAndWipeUseCase = SignOutAndWipeUseCase(auth, local),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined + CoroutineExceptionHandler { _, _ -> })
        )
        val vm = SyncStatusViewModel(auth, coordinator, connectivity, timeSource)
    }

    private fun storeWithLast(at: Instant? = last): DataStore<Preferences> =
        if (at == null) FakeSettingsDataStore() else FakeSettingsDataStore(preferencesOf(LAST_SUCCESSFUL_SYNC_KEY to at.toString()))

    @Test
    fun arranque_en_frio_con_auth_en_Loading_oculta_la_pildora() = runTest {
        val env = Env(AuthState.Loading, dataStore = storeWithLast())
        assertNull(env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun el_primer_estado_estable_muestra_la_pildora() = runTest {
        val env = Env(AuthState.Loading, dataStore = storeWithLast())
        assertNull(env.vm.indicator.value)

        env.auth.setState(signedIn)

        assertEquals(SyncIndicator.Synced(last), env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun tras_el_primer_estable_un_Loading_no_oculta_ni_cambia_la_pildora() = runTest {
        val env = Env(signedIn, dataStore = storeWithLast())
        assertEquals(SyncIndicator.Synced(last), env.vm.indicator.value)

        env.auth.setState(AuthState.Loading)

        assertEquals(SyncIndicator.Synced(last), env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun tras_un_estable_sin_sesion_un_Loading_conserva_Guest() = runTest {
        val env = Env(AuthState.NotAuthenticated)
        assertEquals(SyncIndicator.Guest, env.vm.indicator.value)

        env.auth.setState(AuthState.Loading)

        assertEquals(SyncIndicator.Guest, env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun un_cambio_real_de_estable_Authenticated_a_NotAuthenticated_se_refleja() = runTest {
        val env = Env(signedIn, dataStore = storeWithLast())
        assertEquals(SyncIndicator.Synced(last), env.vm.indicator.value)

        env.auth.setState(AuthState.NotAuthenticated)

        assertEquals(SyncIndicator.Guest, env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun un_cambio_de_estable_no_se_pierde_por_pasar_antes_por_Loading() = runTest {
        val env = Env(signedIn, dataStore = storeWithLast())

        env.auth.setState(AuthState.Loading)
        env.auth.setState(AuthState.NotAuthenticated)

        assertEquals(SyncIndicator.Guest, env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun sin_conectividad_la_pildora_es_Offline_y_vuelve_al_recuperarla() = runTest {
        val env = Env(signedIn, dataStore = storeWithLast())

        env.connectivity.online.value = false
        assertEquals(SyncIndicator.Offline, env.vm.indicator.value)

        env.connectivity.online.value = true
        assertEquals(SyncIndicator.Synced(last), env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun mientras_la_lectura_de_la_ultima_sync_no_termina_es_Unknown_y_despues_el_valor_real() = runTest {
        val gated = GatedDataStore(storeWithLast())
        val env = Env(signedIn, dataStore = gated)
        assertEquals(SyncIndicator.Unknown, env.vm.indicator.value)

        gated.gate.complete(Unit)

        assertEquals(SyncIndicator.Synced(last), env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun lectura_terminada_sin_valor_es_NeverSynced_pero_antes_es_Unknown() = runTest {
        val gated = GatedDataStore(storeWithLast(null))
        val env = Env(signedIn, dataStore = gated)
        assertEquals(SyncIndicator.Unknown, env.vm.indicator.value)

        gated.gate.complete(Unit)

        assertEquals(SyncIndicator.NeverSynced, env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun invitado_y_sin_conexion_no_esperan_a_la_lectura() = runTest {
        val gated = GatedDataStore(storeWithLast())
        val guest = Env(AuthState.NotAuthenticated, dataStore = gated)
        assertEquals(SyncIndicator.Guest, guest.vm.indicator.value)

        val offline = Env(signedIn, online = false, dataStore = GatedDataStore(storeWithLast()))
        assertEquals(SyncIndicator.Offline, offline.vm.indicator.value)
        guest.coordinator.close()
        offline.coordinator.close()
    }

    @Test
    fun el_indicador_de_lectura_terminada_pasa_a_true_aunque_la_lectura_falle() = runTest {
        val gated = GatedDataStore(storeWithLast(), failRead = true)
        val env = Env(signedIn, dataStore = gated)
        assertEquals(false, env.coordinator.lastSuccessfulSyncAtLoaded.value)

        gated.gate.complete(Unit)

        assertTrue(env.coordinator.lastSuccessfulSyncAtLoaded.value)
        env.coordinator.close()
    }

    @Test
    fun el_indicador_de_lectura_terminada_es_false_hasta_que_termina_y_true_despues() = runTest {
        val gated = GatedDataStore(storeWithLast())
        val env = Env(signedIn, dataStore = gated)
        assertEquals(false, env.coordinator.lastSuccessfulSyncAtLoaded.value)

        gated.gate.complete(Unit)

        assertTrue(env.coordinator.lastSuccessfulSyncAtLoaded.value)
        assertEquals(last, env.coordinator.lastSuccessfulSyncAt.value)
        env.coordinator.close()
    }

    // --- Gracia del error al reconectar (solo presentacion) ---

    private val userSignedIn = AuthState.Authenticated("user-1", "a@b.c", sessionConfirmed = true)

    private fun pendingDeck() =
        Deck("d1", "d1", Instant.fromEpochMilliseconds(1000), Instant.fromEpochMilliseconds(2000), null, userId = "user-1")

    /** Entorno con un sync ya fallido (PUSH_PARTIAL) y con red; el tiempo es el virtual del runTest. */
    private suspend fun TestScope.envWithFailedSync(): Env {
        val env = Env(userSignedIn, dataStore = storeWithLast(), timeSource = testScheduler.timeSource)
        env.remote.pushDeckIdsThatFail = setOf("d1")
        env.decks.addDeck(pendingDeck())
        env.coordinator.syncNow()
        return env
    }

    private val partial = SyncIndicator.Error(SyncErrorKind.PUSH_PARTIAL)

    @Test
    fun un_error_con_la_red_estable_se_muestra_sin_gracia() = runTest {
        val env = envWithFailedSync()

        assertEquals(partial, env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun al_reconectar_el_error_viejo_no_se_muestra_si_el_sync_acaba_dentro_de_la_gracia() = runTest {
        val env = envWithFailedSync()
        val seen = mutableListOf<SyncIndicator?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.indicator.collect { seen += it } }
        env.connectivity.online.value = false
        assertEquals(SyncIndicator.Offline, env.vm.indicator.value)
        val mark = seen.size

        env.connectivity.online.value = true
        assertEquals(SyncIndicator.Offline, env.vm.indicator.value)
        advanceTimeBy(1000)
        env.remote.pushDeckIdsThatFail = emptySet()
        env.coordinator.syncNow()
        advanceTimeBy(10_000)
        runCurrent()

        assertTrue(seen.drop(mark).none { it is SyncIndicator.Error }, "se vio un error tras reconectar: $seen")
        assertEquals(SyncIndicator.Synced(env.coordinator.lastSuccessfulSyncAt.value!!), env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun al_reconectar_el_error_que_persiste_se_muestra_justo_al_acabar_la_gracia() = runTest {
        val env = envWithFailedSync()
        env.connectivity.online.value = false
        env.connectivity.online.value = true

        advanceTimeBy(SYNC_ERROR_GRACE_AFTER_RECONNECT.inWholeMilliseconds - 1)
        runCurrent()
        assertEquals(SyncIndicator.Offline, env.vm.indicator.value)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(partial, env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun si_se_pierde_la_red_durante_la_gracia_no_aparece_el_error_viejo() = runTest {
        val env = envWithFailedSync()
        env.connectivity.online.value = false
        env.connectivity.online.value = true
        advanceTimeBy(1000)

        env.connectivity.online.value = false
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(SyncIndicator.Offline, env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun una_segunda_reconexion_empieza_una_gracia_nueva() = runTest {
        val env = envWithFailedSync()
        env.connectivity.online.value = false
        env.connectivity.online.value = true
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(partial, env.vm.indicator.value)

        env.connectivity.online.value = false
        advanceTimeBy(10_000)
        env.connectivity.online.value = true

        assertEquals(SyncIndicator.Offline, env.vm.indicator.value)
        env.coordinator.close()
    }

    @Test
    fun errorGraceRemaining_tabla() {
        val g = 3.seconds
        val failed = SyncIndicator.Error(SyncErrorKind.FAILED)
        val cases: List<Triple<String, Duration, Duration>> = listOf(
            Triple("FAILED recien reconectado", errorGraceRemaining(failed, 0.milliseconds, g), 3.seconds),
            Triple("PUSH_PARTIAL a mitad", errorGraceRemaining(partial, 1.seconds, g), 2.seconds),
            Triple("justo al acabar", errorGraceRemaining(failed, 3.seconds, g), Duration.ZERO),
            Triple("pasada", errorGraceRemaining(failed, 9.seconds, g), Duration.ZERO),
            Triple("LOCAL sin gracia", errorGraceRemaining(SyncIndicator.Error(SyncErrorKind.LOCAL), 0.milliseconds, g), Duration.ZERO),
            Triple("REJECTED sin gracia", errorGraceRemaining(SyncIndicator.Error(SyncErrorKind.REJECTED), 0.milliseconds, g), Duration.ZERO),
            Triple("sin reconexion", errorGraceRemaining(failed, null, g), Duration.ZERO),
            Triple("no es error", errorGraceRemaining(SyncIndicator.Syncing, 0.milliseconds, g), Duration.ZERO),
            Triple("Offline", errorGraceRemaining(SyncIndicator.Offline, 0.milliseconds, g), Duration.ZERO),
            Triple("null", errorGraceRemaining(null, 0.milliseconds, g), Duration.ZERO)
        )
        cases.forEach { (name, actual, expected) -> assertEquals(expected, actual, name) }
    }
}
