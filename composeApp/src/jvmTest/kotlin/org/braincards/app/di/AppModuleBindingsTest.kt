package org.braincards.app.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.repository.LocalDataRepositoryImpl
import org.braincards.app.domain.repository.LocalDataRepository
import org.braincards.app.domain.repository.LocalTableInspector
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Enlaces (bind) de appModule comprobados con un Koin REAL sin instancias tempranas (no crea Supabase ni el sync). */
class AppModuleBindingsTest {

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
}
