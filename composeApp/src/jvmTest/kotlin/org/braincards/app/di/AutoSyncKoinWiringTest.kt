package org.braincards.app.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
import org.braincards.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.flowOf
import org.braincards.app.domain.repository.AutoSyncPreference
import org.braincards.app.domain.repository.PendingChangesObserver
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import org.braincards.app.platform.DesktopConnectivityMonitor
import org.braincards.app.platform.DesktopForegroundMonitor
import org.braincards.app.presentation.sync.AutoSyncController
import org.braincards.app.presentation.sync.AutoSyncPauseGate
import org.braincards.app.presentation.sync.SyncCoordinator
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Cableado REAL de la sync automatica en Koin (autoSyncModule + desktopSyncModule + un modulo de prueba con el
 * coordinador real y fakes): el controlador arranca solo, un monitor de primer plano / de conexion provoca una sync y
 * al cerrar Koin (o al cerrar la ventana) se para y no sigue sincronizando. Tiempo real y plazos por defecto.
 *
 * NO cubre: los monitores de Android, ni el AppModule completo (crearia el cliente real de Supabase, que lee la sesion
 * guardada del equipo): solo las definiciones propias de la sync automatica.
 */
class AutoSyncKoinWiringTest {

    private val userId = "user-1"
    private val remote = FakeRemoteDataSource()
    private val online = AtomicBoolean(true)
    private val probes = AtomicInteger(0)
    private val scopes = mutableListOf<CoroutineScope>()
    private val pending = MutableStateFlow(PendingStamp(0, 0))

    private val pulls get() = remote.calls.count { it == "pullDecks" }

    @AfterTest
    fun tearDown() {
        runCatching { stopKoin() }
        scopes.forEach { it.cancel() }
    }

    /** Un coordinador real sobre fakes, un observador de pendientes vacio y la puerta de pausa. */
    private fun testBaseModule(): org.koin.core.module.Module {
        val auth = FakeAuthRepository(AuthState.Authenticated(userId, "a@b.c", sessionConfirmed = true))
        val decks = FakeDecksRepository()
        val topics = FakeTopicsRepository()
        val flashcards = FakeFlashcardRepository()
        val scheduling = FakeCardSchedulingRepository()
        val local = FakeLocalDataRepository()
        val gate = AutoSyncPauseGate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
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
            coroutineScope = scope,
            pauseGate = gate
        )
        return module {
            single<AuthRepository> { auth }
            single<PendingChangesObserver> {
                object : PendingChangesObserver {
                    override fun observe(userId: String) = pending
                }
            }
            single { gate }
            single { coordinator }
            // autoSyncModule() lee el interruptor de sync automatico: aqui, siempre activado.
            single<AutoSyncPreference> {
                object : AutoSyncPreference {
                    override val enabled = flowOf(true)
                    override suspend fun setEnabled(value: Boolean) = Unit
                }
            }
        }
    }

    private fun connectivityMonitor() = DesktopConnectivityMonitor(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it },
        interval = 50.milliseconds,
        probe = { probes.incrementAndGet(); online.get() }
    )

    private fun startWiring(foreground: DesktopForegroundMonitor) {
        startKoin {
            modules(testBaseModule(), autoSyncModule(), desktopSyncModule(foreground, ::connectivityMonitor))
        }
    }

    /**
     * Prueba de que el controlador sigue VIVO: con cambios pendientes y la app pasando a segundo plano hace un ultimo
     * intento que se salta el intervalo minimo (el primer plano normal no sirve: lo frena el intervalo de 30 s).
     */
    private suspend fun provokeBackgroundAttempt(foreground: DesktopForegroundMonitor) {
        pending.value = PendingStamp(1, 100)
        delay(150)
        foreground.onWindowMinimizedChanged(true)
        delay(500)
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }

    @Test
    fun el_controlador_se_crea_y_arranca_solo_al_iniciar_koin_y_el_primer_plano_provoca_una_sync() = runBlocking<Unit> {
        val foreground = DesktopForegroundMonitor(initiallyVisible = false)

        startWiring(foreground)       // nadie pide el controlador: lo crea createdAtStart
        delay(400)
        assertEquals(0, pulls, "con la ventana minimizada no sincroniza")

        foreground.onWindowMinimizedChanged(false) // se restaura la ventana
        waitUntil { pulls == 1 }
        delay(300)

        assertEquals(1, pulls, "una sola sync")
    }

    @Test
    fun hay_una_unica_instancia_del_controlador() = runBlocking<Unit> {
        startWiring(DesktopForegroundMonitor())

        val koin = GlobalContext.get()

        assertSame(koin.get<AutoSyncController>(), koin.get<AutoSyncController>())
    }

    @Test
    fun la_conexion_provoca_la_sync_cuando_vuelve_la_red() = runBlocking<Unit> {
        online.set(false)
        startWiring(DesktopForegroundMonitor())   // ventana visible, sesion confirmada, pero sin red
        delay(400)
        assertEquals(0, pulls, "sin conexion no sincroniza")

        online.set(true)                          // el sondeo (cada 50 ms en el test) lo detecta
        waitUntil { pulls == 1 }
        delay(300)

        assertEquals(1, pulls)
    }

    @Test
    fun control_positivo_un_controlador_en_marcha_hace_el_ultimo_intento_al_pasar_a_segundo_plano() = runBlocking<Unit> {
        val foreground = DesktopForegroundMonitor()
        startWiring(foreground)
        waitUntil { pulls == 1 }

        provokeBackgroundAttempt(foreground)

        assertEquals(2, pulls, "con el controlador vivo, el ultimo intento en segundo plano si sincroniza")
    }

    @Test
    fun al_cerrar_koin_el_controlador_se_para_y_los_monitores_se_cierran() = runBlocking<Unit> {
        val foreground = DesktopForegroundMonitor()
        startWiring(foreground)
        waitUntil { pulls == 1 }
        val probesBeforeStop = probes.get()
        assertTrue(probesBeforeStop >= 1)

        stopKoin()
        delay(300)
        val probesAfterStop = probes.get()
        provokeBackgroundAttempt(foreground) // ya no hay controlador que haga el ultimo intento

        assertEquals(1, pulls, "ninguna sync tras cerrar Koin")
        delay(300)
        assertEquals(probesAfterStop, probes.get(), "el sondeo de conectividad se cerro")
    }

    @Test
    fun shutdownAutoSync_para_el_controlador_y_cierra_la_conectividad_sin_parar_koin() = runBlocking<Unit> {
        val foreground = DesktopForegroundMonitor()
        startWiring(foreground)
        waitUntil { pulls == 1 }

        shutdownAutoSync()
        delay(300)
        val probesAfter = probes.get()
        provokeBackgroundAttempt(foreground)

        assertEquals(1, pulls, "parado el controlador, el ultimo intento en segundo plano no sincroniza")
        delay(300)
        assertEquals(probesAfter, probes.get(), "conectividad cerrada")
        assertTrue(GlobalContext.getOrNull() != null, "Koin sigue en marcha: stopKoin() va despues de la aplicacion de Compose")
    }

    @Test
    fun shutdownAutoSync_sin_koin_arrancado_no_falla() {
        runCatching { stopKoin() }

        shutdownAutoSync()

        assertFalse(GlobalContext.getOrNull() != null)
    }

    @Test
    fun la_ventana_minimizada_pone_segundo_plano_y_restaurada_primer_plano() = runBlocking<Unit> {
        val monitor = DesktopForegroundMonitor()
        val minimized = MutableStateFlow(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
        scope.launch { monitor.followWindow(minimized) }

        waitUntil { monitor.isForeground.value }
        minimized.value = true
        waitUntil { !monitor.isForeground.value }
        minimized.value = false
        waitUntil { monitor.isForeground.value }
    }

    @OptIn(KoinInternalApi::class) // inspecciona las definiciones del modulo sin arrancar Koin
    @Test
    fun appModule_incluye_el_controlador_y_registra_el_observador_de_pendientes() {
        // Solo se CONSTRUYE el modulo (las definiciones son perezosas): no se crea el cliente de Supabase ni nada.
        val module = appModule(
            databaseBuilder = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()),
            settingsDataStore = FakeSettingsDataStore()
        )

        val types = (listOf(module) + module.includedModules).flatMap { m ->
            m.mappings.values.map { it.beanDefinition.primaryType }
        }

        assertTrue(AutoSyncController::class in types, "appModule incluye autoSyncModule(): el controlador esta registrado")
        assertTrue(PendingChangesObserver::class in types, "appModule registra el observador de pendientes")
        assertTrue(AutoSyncPreference::class in types, "appModule registra el interruptor de sync automatico")
        assertTrue(AutoSyncPauseGate::class in types)
    }
}
