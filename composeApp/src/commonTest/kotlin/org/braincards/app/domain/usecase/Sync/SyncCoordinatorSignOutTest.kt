package org.braincards.app.domain.usecase.Sync

import org.braincards.app.data.remote.SupabaseFailureClassifier
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeResult
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.presentation.sync.FinishSignOutResult
import org.braincards.app.presentation.sync.SYNC_BEFORE_SIGN_OUT_TIMEOUT
import org.braincards.app.presentation.sync.SignOutPrompt
import org.braincards.app.presentation.sync.SyncCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cierre de sesion del SyncCoordinator (prepareSignOut / finishSignOut / resetSyncState).
 * Todo con runTest: el coordinador usa backgroundScope (mismo reloj virtual), el sync es el
 * REAL (SyncEngineUseCase) contra FakeRemoteDataSource, asi que el limite de tiempo se prueba
 * sobre el camino real del sync, no sobre un stub.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorSignOutTest {

    private val userId = "user-1"
    private val themeKey = stringPreferencesKey("app_theme")
    private val lastSyncKey = stringPreferencesKey("last_successful_sync_at")

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    private class Env(
        scope: CoroutineScope,
        initialAuth: AuthState,
        initialPrefs: Preferences
    ) {
        val log = mutableListOf<String>()
        val auth = FakeAuthRepository(initialState = initialAuth, callLog = log)
        val remote = FakeRemoteDataSource()
        val decks = FakeDecksRepository()
        val topics = FakeTopicsRepository()
        val flashcards = FakeFlashcardRepository()
        val scheduling = FakeCardSchedulingRepository()
        val local = FakeLocalDataRepository(log)
        val dataStore = FakeSettingsDataStore(initialPrefs)
        val coordinator = SyncCoordinator(
            authRepository = auth,
            syncEngineUseCase = SyncEngineUseCase(
                authRepository = auth,
                remoteDataSource = remote,
                decksRepository = decks,
                topicsRepository = topics,
                flashcardRepository = flashcards,
                cardSchedulingRepository = scheduling,
                backfillLocalDataUseCase = BackfillLocalDataUseCase(decks, topics, flashcards, scheduling),
                failureClassifier = SupabaseFailureClassifier
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
    }

    private fun TestScope.env(
        confirmed: Boolean = true,
        prefs: Preferences = androidx.datastore.preferences.core.emptyPreferences()
    ): Env {
        val env = Env(backgroundScope, AuthState.Authenticated(userId, "a@b.c", confirmed), prefs)
        runCurrent() // deja que el init del coordinador corra (ultima sync guardada y conteo de pendientes; ya no sincroniza)
        return env
    }

    private fun deck(id: String, deleted: Boolean = false) = Deck(
        id = id, name = id, createdAt = ms(1000), updatedAt = ms(2000), description = null,
        deletedAt = if (deleted) ms(2000) else null, userId = userId, lastSyncedAt = null
    )

    // --- condicion 1: el mutex NO se mantiene mientras la UI muestra el dialogo ---

    @Test
    fun con_el_dialogo_abierto_otro_sync_se_ejecuta_sin_bloquearse() = runTest {
        val env = env()
        env.remote.pushDeckIdsThatFail = setOf("d1") // el push falla: queda 1 cambio sin subir
        env.decks.addDeck(deck("d1"))

        val prompt = env.coordinator.prepareSignOut()
        assertEquals(SignOutPrompt.NeedsConfirmation(1), prompt)

        // "Dialogo abierto": entre prepare y finish. Si prepare siguiera con el mutex, este
        // syncNow() nunca terminaria.
        val pullsBefore = env.remote.calls.count { it == "pullDecks" }
        val job = launch { env.coordinator.syncNow() }
        runCurrent()

        assertTrue(job.isCompleted, "syncNow() se quedo bloqueado: prepareSignOut no solto el mutex")
        assertEquals(pullsBefore + 1, env.remote.calls.count { it == "pullDecks" })
    }

    // --- condicion 2: tiempo limite del sync previo (camino real del sync) ---

    @Test
    fun el_sync_previo_se_corta_en_el_limite_y_se_sigue_con_el_conteo_local() = runTest {
        val env = env()
        env.decks.addDeck(deck("d1"))
        env.remote.pullDecksDelayMillis = 10 * SYNC_BEFORE_SIGN_OUT_TIMEOUT.inWholeMilliseconds
        val start = testScheduler.currentTime

        val prompt = env.coordinator.prepareSignOut()

        // Tiempo virtual: espera exactamente el limite, no lo que tarda el pull colgado.
        assertEquals(SYNC_BEFORE_SIGN_OUT_TIMEOUT.inWholeMilliseconds, testScheduler.currentTime - start)
        // El pull se corto antes del push: el cambio sigue sin subir y se cuenta.
        assertEquals(SignOutPrompt.NeedsConfirmation(1), prompt)
        // El sync cortado no deja el coordinador "sincronizando" ni el mutex tomado.
        assertFalse(env.coordinator.isSyncing.value)
        env.remote.pullDecksDelayMillis = 0
        val job = launch { env.coordinator.syncNow() }
        runCurrent()
        assertTrue(job.isCompleted)
    }

    @Test
    fun si_el_pull_y_el_push_del_sync_previo_fallan_se_sigue_con_el_conteo_local() = runTest {
        val env = env()
        env.decks.addDeck(deck("d1"))
        env.remote.pushDeckIdsThatFail = setOf("d1")
        env.remote.pullDecksFailure = IllegalStateException("sin red")

        assertEquals(SignOutPrompt.NeedsConfirmation(1), env.coordinator.prepareSignOut())
    }

    @Test
    fun sin_sesion_confirmada_no_se_intenta_el_sync_solo_conteo_local() = runTest {
        val env = env(confirmed = false)
        env.decks.addDeck(deck("d1"))
        val callsBefore = env.remote.calls.size

        val prompt = env.coordinator.prepareSignOut()

        assertEquals(callsBefore, env.remote.calls.size, "no debe haber llamadas al remoto sin sesion confirmada")
        assertEquals(SignOutPrompt.NeedsConfirmation(1), prompt)
    }

    // --- condicion 3: formula del conteo ---

    @Test
    fun el_conteo_suma_pendientes_de_las_4_repos_con_tombstones_mas_filas_sin_dueno() = runTest {
        val env = env(confirmed = false) // offline: nada de sync, solo conteo local
        env.decks.addDeck(deck("d1"))
        env.decks.addDeck(deck("d-borrado", deleted = true)) // tombstone pendiente
        env.topics.addTopic(Topic("t1", "d1", "T", ms(1000), ms(2000), userId = userId))
        env.flashcards.addFlashcard(
            Flashcard("f1", "d1", "Q", "A", ms(1000), ms(2000), CardType.FLIP, userId = userId)
        )
        env.scheduling.upsert(
            CardScheduling("f1", 2.5, 1, 1, ms(5000), ms(2000), userId = userId, lastSyncedAt = null)
        )
        env.local.unownedRows = 3

        // 5 pendientes (2 decks incl. tombstone + 1 topic + 1 flashcard + 1 scheduling) + 3 sin dueno
        assertEquals(SignOutPrompt.NeedsConfirmation(8), env.coordinator.prepareSignOut())
    }

    // --- condicion 6: ReadyToSignOut cuando no hay nada que perder (tambien offline) ---

    @Test
    fun ready_to_sign_out_online_cuando_todo_esta_sincronizado() = runTest {
        val env = env()
        env.decks.addDeck(deck("d1")) // el sync previo lo sube

        assertEquals(SignOutPrompt.ReadyToSignOut, env.coordinator.prepareSignOut())
    }

    @Test
    fun ready_to_sign_out_offline_cuando_no_hay_nada_pendiente() = runTest {
        val env = env(confirmed = false)

        assertEquals(SignOutPrompt.ReadyToSignOut, env.coordinator.prepareSignOut())
        assertTrue(env.remote.calls.isEmpty())
    }

    // --- condicion 4: reconfirmacion si el conteo crecio ---

    @Test
    fun si_el_conteo_crecio_tras_el_dialogo_pide_reconfirmar_y_no_borra_nada() = runTest {
        val env = env(confirmed = false)
        env.decks.addDeck(deck("d1"))
        val prompt = env.coordinator.prepareSignOut()
        assertEquals(SignOutPrompt.NeedsConfirmation(1), prompt)

        env.decks.addDeck(deck("d2")) // el usuario edita con el dialogo abierto

        val result = env.coordinator.finishSignOut(userId, confirmedCount = 1)

        assertEquals(FinishSignOutResult.NeedsReconfirmation(2), result)
        assertEquals(0, env.local.wipeCallCount)
        assertFalse(env.auth.signOutCalled)
        assertFalse(env.auth.clearLocalSessionCalled)
    }

    @Test
    fun si_el_conteo_no_crecio_borra_y_cierra_sesion() = runTest {
        val env = env(confirmed = false)
        env.decks.addDeck(deck("d1"))

        val result = env.coordinator.finishSignOut(userId, confirmedCount = 1)

        assertEquals(FinishSignOutResult.Finished(SignOutAndWipeResult.SignedOut), result)
        assertEquals(listOf("wipeAll", "signOut"), env.log)
    }

    @Test
    fun cancelar_el_dialogo_no_borra_nada_prepare_solo_no_toca_la_bd_ni_la_sesion() = runTest {
        val env = env(confirmed = false)
        env.decks.addDeck(deck("d1"))

        env.coordinator.prepareSignOut() // el usuario ve el dialogo y pulsa Cancelar: no hay finish

        assertEquals(0, env.local.wipeCallCount)
        assertTrue(env.log.isEmpty())
        assertEquals(AuthState.Authenticated(userId, "a@b.c", false), env.auth.authState.first())
    }

    // --- condicion 6: resultados del use case propagados ---

    @Test
    fun session_changed_se_propaga_y_no_borra_nada() = runTest {
        val env = env(confirmed = false)

        val result = env.coordinator.finishSignOut("otro-usuario", confirmedCount = 0)

        assertEquals(FinishSignOutResult.Finished(SignOutAndWipeResult.SessionChanged), result)
        assertEquals(0, env.local.wipeCallCount)
    }

    @Test
    fun wipe_failed_se_propaga_y_conserva_el_estado_de_sync() = runTest {
        val boom = IllegalStateException("disco lleno")
        val env = env(
            confirmed = false,
            prefs = preferencesOf(lastSyncKey to "2026-01-01T00:00:00Z")
        )
        env.local.wipeFailure = boom

        val result = env.coordinator.finishSignOut(userId, confirmedCount = 0)

        val finished = assertIs<FinishSignOutResult.Finished>(result)
        val failed = assertIs<SignOutAndWipeResult.WipeFailed>(finished.outcome)
        assertEquals(boom, failed.cause)
        assertFalse(env.auth.signOutCalled)
        // Nada se borro: el estado de sync sigue ahi.
        assertNotNull(env.coordinator.lastSuccessfulSyncAt.value)
        assertEquals("2026-01-01T00:00:00Z", env.dataStore.data.first()[lastSyncKey])
    }

    @Test
    fun session_close_failed_se_propaga_y_resetea_el_estado_de_sync_porque_la_bd_ya_se_vacio() = runTest {
        val env = env(
            confirmed = false,
            prefs = preferencesOf(lastSyncKey to "2026-01-01T00:00:00Z")
        )
        env.auth.signOutFailure = RuntimeException("sin red")
        env.auth.clearLocalSessionFailure = IllegalStateException("no se pudo")

        val result = env.coordinator.finishSignOut(userId, confirmedCount = 0)

        val finished = assertIs<FinishSignOutResult.Finished>(result)
        assertIs<SignOutAndWipeResult.SessionCloseFailed>(finished.outcome)
        assertTrue(env.local.wipeCompleted)
        assertNull(env.coordinator.lastSuccessfulSyncAt.value)
        assertNull(env.dataStore.data.first()[lastSyncKey])
    }

    // --- DataStore: se borra por clave, app_theme se conserva ---

    @Test
    fun tras_cerrar_sesion_se_borra_last_successful_sync_at_pero_se_conserva_app_theme() = runTest {
        val env = env(
            confirmed = false,
            prefs = preferencesOf(
                themeKey to "DARK",
                lastSyncKey to "2026-01-01T00:00:00Z"
            )
        )
        assertNotNull(env.coordinator.lastSuccessfulSyncAt.value)

        val result = env.coordinator.finishSignOut(userId, confirmedCount = 0)

        assertEquals(FinishSignOutResult.Finished(SignOutAndWipeResult.SignedOut), result)
        val prefs = env.dataStore.data.first()
        assertNull(prefs[lastSyncKey])
        assertEquals("DARK", prefs[themeKey], "app_theme debe conservarse: se borra por clave, nunca con clear()")
        assertNull(env.coordinator.lastSuccessfulSyncAt.value)
        assertEquals(0, env.coordinator.pendingChangesCount.value)
    }

    @Test
    fun resetSyncState_por_si_solo_tambien_conserva_app_theme() = runTest {
        val env = env(
            confirmed = false,
            prefs = preferencesOf(themeKey to "LIGHT", lastSyncKey to "2026-01-01T00:00:00Z")
        )

        env.coordinator.resetSyncState()
        advanceUntilIdle()

        val prefs = env.dataStore.data.first()
        assertNull(prefs[lastSyncKey])
        assertEquals("LIGHT", prefs[themeKey])
    }

    // --- marca de agua del pull incremental: se borra con el resto del estado de sync ---------------------------

    // Nombre real de la clave en disco (literal a proposito: si alguien la renombra, el test debe saberlo).
    private val watermarkKey = stringPreferencesKey("pull_watermark")
    private val watermarkJson = """{"v":1,"userId":"user-1","cursors":{"decks":{"ts":"2026-10-22T10:00:00Z","id":"d"}}}"""

    private fun watermarkPrefs() = preferencesOf(
        themeKey to "DARK",
        lastSyncKey to "2026-01-01T00:00:00Z",
        watermarkKey to watermarkJson
    )

    @Test
    fun resetSyncState_borra_la_marca_de_agua_y_el_ultimo_sync_pero_conserva_app_theme() = runTest {
        val env = env(confirmed = false, prefs = watermarkPrefs())

        env.coordinator.resetSyncState()

        val prefs = env.dataStore.data.first()
        assertNull(prefs[watermarkKey], "la marca de agua se borra")
        assertNull(prefs[lastSyncKey], "las demas claves de sync siguen borrandose")
        assertEquals("DARK", prefs[themeKey], "app_theme se conserva (no se usa clear())")
    }

    @Test
    fun cerrar_sesion_borra_la_marca_de_agua_y_conserva_app_theme() = runTest {
        val env = env(confirmed = false, prefs = watermarkPrefs())

        val result = env.coordinator.finishSignOut(userId, confirmedCount = 0)

        assertEquals(FinishSignOutResult.Finished(SignOutAndWipeResult.SignedOut), result)
        val prefs = env.dataStore.data.first()
        assertNull(prefs[watermarkKey])
        assertEquals("DARK", prefs[themeKey])
    }

    @Test
    fun si_falla_el_borrado_de_la_bd_la_marca_de_agua_se_conserva() = runTest {
        val env = env(confirmed = false, prefs = watermarkPrefs())
        env.local.wipeFailure = IllegalStateException("disco lleno")

        env.coordinator.finishSignOut(userId, confirmedCount = 0)

        assertEquals(watermarkJson, env.dataStore.data.first()[watermarkKey], "nada se borro: la marca sigue valiendo")
    }

    @Test
    fun si_la_bd_se_vacio_pero_no_se_pudo_cerrar_la_sesion_la_marca_de_agua_tambien_se_borra() = runTest {
        val env = env(confirmed = false, prefs = watermarkPrefs())
        env.auth.signOutFailure = RuntimeException("sin red")
        env.auth.clearLocalSessionFailure = IllegalStateException("no se pudo")

        env.coordinator.finishSignOut(userId, confirmedCount = 0)

        assertNull(env.dataStore.data.first()[watermarkKey], "la BD esta vacia: una marca vieja dejaria la BD sin repoblar")
    }
}
