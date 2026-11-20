package org.braincards.app.domain.usecase.Sync

import org.braincards.app.data.remote.SupabaseFailureClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import org.braincards.app.presentation.sync.SyncCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El contador de pendientes de la UI sale del PendingChangesObserver (reactivo), no de un recuento al acabar un sync:
 * sube en cuanto hay un cambio local, aunque no haya conexion y no se sincronice nunca.
 */
class SyncCoordinatorPendingCountTest {

    /** Observador falso: un flujo por usuario, controlado desde el test. */
    private class FakeObserver : PendingChangesObserver {
        val flows = mutableMapOf<String, MutableStateFlow<PendingStamp>>()
        fun flow(userId: String) = flows.getOrPut(userId) { MutableStateFlow(PendingStamp(0, 0)) }
        fun set(userId: String, count: Int) { flow(userId).value = PendingStamp(count, count.toLong()) }
        override fun observe(userId: String): Flow<PendingStamp> = flow(userId)
    }

    private class Env(initial: AuthState) {
        val auth = FakeAuthRepository(initial)
        val remote = FakeRemoteDataSource()
        val observer = FakeObserver()
        val decks = FakeDecksRepository()
        val topics = FakeTopicsRepository()
        val flashcards = FakeFlashcardRepository()
        val scheduling = FakeCardSchedulingRepository()
        val local = FakeLocalDataRepository()
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
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            pendingObserver = observer
        )
    }

    private fun authenticated(userId: String) = AuthState.Authenticated(userId, "a@b.c", sessionConfirmed = true)

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(5) }
    }

    @Test
    fun el_contador_sube_a_1_al_emitir_el_observador_sin_ningun_sync() = runBlocking<Unit> {
        val env = Env(authenticated("u1"))

        env.observer.set("u1", 1)

        waitUntil { env.coordinator.pendingChangesCount.value == 1 }
        assertTrue(env.remote.calls.isEmpty(), "ni se ha sincronizado: el contador es reactivo")
    }

    @Test
    fun el_contador_baja_cuando_los_pendientes_se_suben() = runBlocking<Unit> {
        val env = Env(authenticated("u1"))
        env.observer.set("u1", 3)
        waitUntil { env.coordinator.pendingChangesCount.value == 3 }

        env.observer.set("u1", 0)

        waitUntil { env.coordinator.pendingChangesCount.value == 0 }
    }

    @Test
    fun un_sync_no_pisa_el_contador_del_observador() = runBlocking<Unit> {
        // Los repositorios falsos no tienen nada pendiente (un recuento al acabar el sync daria 0), pero el observador
        // dice 5 (p. ej. filas sin dueno que getPendingSync no ve): manda el observador.
        val env = Env(authenticated("u1"))
        env.observer.set("u1", 5)
        waitUntil { env.coordinator.pendingChangesCount.value == 5 }

        env.coordinator.syncNow()

        assertEquals(5, env.coordinator.pendingChangesCount.value)
    }

    @Test
    fun al_cambiar_de_usuario_se_resuscribe_al_flujo_del_nuevo() = runBlocking<Unit> {
        val env = Env(authenticated("u1"))
        env.observer.set("u1", 2)
        env.observer.set("u2", 7)
        waitUntil { env.coordinator.pendingChangesCount.value == 2 }

        env.auth.setState(authenticated("u2"))
        waitUntil { env.coordinator.pendingChangesCount.value == 7 }

        env.observer.set("u1", 9) // el usuario anterior ya no cuenta
        delay(100)
        assertEquals(7, env.coordinator.pendingChangesCount.value)
        assertEquals(0, env.observer.flow("u1").subscriptionCount.value, "sin suscripcion al usuario anterior")
    }

    @Test
    fun sin_sesion_el_contador_es_0_y_al_volver_a_entrar_se_recupera() = runBlocking<Unit> {
        val env = Env(authenticated("u1"))
        env.observer.set("u1", 4)
        waitUntil { env.coordinator.pendingChangesCount.value == 4 }

        env.auth.setState(AuthState.NotAuthenticated)
        waitUntil { env.coordinator.pendingChangesCount.value == 0 }

        env.auth.setState(authenticated("u1"))
        waitUntil { env.coordinator.pendingChangesCount.value == 4 }
    }

    @Test
    fun resetSyncState_pone_el_contador_a_0() = runBlocking<Unit> {
        val env = Env(authenticated("u1"))
        env.observer.set("u1", 4)
        waitUntil { env.coordinator.pendingChangesCount.value == 4 }

        env.coordinator.resetSyncState()

        assertEquals(0, env.coordinator.pendingChangesCount.value)
    }

    @Test
    fun close_cancela_la_suscripcion_y_el_contador_deja_de_cambiar() = runBlocking<Unit> {
        val env = Env(authenticated("u1"))
        env.observer.set("u1", 1)
        waitUntil { env.coordinator.pendingChangesCount.value == 1 }
        assertEquals(1, env.observer.flow("u1").subscriptionCount.value)

        env.coordinator.close()
        env.observer.set("u1", 8)
        delay(100)

        assertEquals(1, env.coordinator.pendingChangesCount.value)
        assertEquals(0, env.observer.flow("u1").subscriptionCount.value, "sin corrutinas colgadas")
        env.coordinator.close() // idempotente
    }
}
