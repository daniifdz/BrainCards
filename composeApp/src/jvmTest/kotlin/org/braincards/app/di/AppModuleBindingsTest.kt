package org.braincards.app.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.repository.DueCardsObserverImpl
import org.braincards.app.data.repository.LocalDataRepositoryImpl
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.DueCardsObserver
import org.braincards.app.domain.repository.LocalDataRepository
import org.braincards.app.domain.repository.LocalTableInspector
import org.braincards.app.domain.repository.RemoteDataSource
import org.braincards.app.domain.usecase.Flashcards.ObserveDueCountUseCase
import org.braincards.app.presentation.screens.auth.WelcomeViewModel
import org.braincards.app.presentation.screens.home.HomeViewModel
import org.braincards.app.presentation.screens.navigation.AppEntryViewModel
import org.braincards.app.data.local.DataStoreLocalModePreference
import org.braincards.app.domain.repository.LocalModePreference
import org.braincards.app.presentation.sync.ConnectivityMonitor
import org.braincards.app.presentation.sync.SyncStatusViewModel
import org.braincards.app.presentation.sync.FakeConnectivityMonitor
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Enlaces (bind) de appModule comprobados con un Koin REAL sin instancias tempranas (no crea Supabase ni el sync). */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModuleBindingsTest {

    @BeforeTest
    fun setUp() {
        // Los ViewModel crean su viewModelScope sobre Dispatchers.Main.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * appModule REAL + un modulo de sustituciones: AuthRepository y RemoteDataSource falsos y el monitor de
     * conectividad de plataforma (que en la app entra por initKoin). El SupabaseClient real NO se debe crear nunca
     * (leeria la sesion guardada del equipo): su definicion se sustituye por una que falla si alguien la pide.
     */
    private fun withRealModule(block: (Koin) -> Unit) {
        val app = koinApplication(createEagerInstances = false) {
            modules(
                appModule(
                    databaseBuilder = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()),
                    settingsDataStore = FakeSettingsDataStore()
                ),
                module {
                    single<SupabaseClient> { error("El SupabaseClient real no debe crearse en este test") }
                    single<AuthRepository> { FakeAuthRepository() }
                    single<RemoteDataSource> { FakeRemoteDataSource() }
                    single<ConnectivityMonitor> { FakeConnectivityMonitor() }
                }
            )
        }
        try {
            block(app.koin)
        } finally {
            runCatching { app.koin.get<AppDatabase>().close() }
            app.close()
        }
    }

    @Test
    fun el_repositorio_de_datos_locales_y_el_inspector_de_tablas_son_el_mismo_singleton() {
        val app = koinApplication(createEagerInstances = false) {
            modules(
                appModule(
                    databaseBuilder = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()),
                    settingsDataStore = FakeSettingsDataStore()
                )
            )
        }
        try {
            val repository = app.koin.get<LocalDataRepository>()
            val inspector = app.koin.get<LocalTableInspector>()

            assertTrue(repository is LocalDataRepositoryImpl)
            assertSame(repository, inspector, "una sola instancia responde a las dos interfaces")
            assertSame(repository, app.koin.get<LocalDataRepositoryImpl>())
        } finally {
            runCatching { app.koin.get<AppDatabase>().close() }
            app.close()
        }
    }

    @Test
    fun el_observador_de_tarjetas_pendientes_se_resuelve_y_es_un_singleton() {
        withRealModule { koin ->
            val observer = koin.get<DueCardsObserver>()

            assertTrue(observer is DueCardsObserverImpl)
            assertSame(observer, koin.get<DueCardsObserver>())
            assertNotNull(koin.get<ObserveDueCountUseCase>())
        }
    }

    @Test
    fun HomeViewModel_se_resuelve_con_sus_dependencias_reales() {
        withRealModule { koin ->
            assertNotNull(koin.get<HomeViewModel>())
        }
    }

    @Test
    fun SyncStatusViewModel_se_resuelve_con_el_coordinador_real_y_los_fakes_de_plataforma() {
        withRealModule { koin ->
            assertNotNull(koin.get<SyncStatusViewModel>())
        }
    }

    @Test
    fun WelcomeViewModel_se_resuelve_con_sus_dependencias_reales() {
        withRealModule { koin ->
            assertNotNull(koin.get<WelcomeViewModel>())
        }
    }

    @Test
    fun AppEntryViewModel_y_el_flag_de_modo_local_se_resuelven_desde_el_modulo_real() {
        withRealModule { koin ->
            val preference = koin.get<LocalModePreference>()

            assertTrue(preference is DataStoreLocalModePreference)
            assertSame(preference, koin.get<LocalModePreference>())
            assertNotNull(koin.get<AppEntryViewModel>())
        }
    }
}
