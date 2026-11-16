package org.braincards.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El interruptor de sync automatico sobre un DataStore REAL en un FICHERO TEMPORAL del test (nunca las preferencias
 * del usuario). Por defecto activado; el valor sobrevive a reabrir el fichero; escribirlo no toca el resto de claves.
 */
class DataStoreAutoSyncPreferenceTest {

    private val themeKey = stringPreferencesKey("app_theme")

    private class Harness(val dir: File) {
        val file = File(dir, "test.preferences_pb")
        private val scopes = mutableListOf<CoroutineScope>()

        fun open(): DataStore<Preferences> {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
            return PreferenceDataStoreFactory.create(scope = scope) { file }
        }

        suspend fun reopen(): DataStore<Preferences> {
            scopes.forEach { it.coroutineContext[Job]!!.cancelAndJoin() }
            scopes.clear()
            return open()
        }

        fun close() {
            scopes.forEach { it.cancel() }
            dir.deleteRecursively()
        }
    }

    private fun withStore(block: suspend (Harness, DataStore<Preferences>) -> Unit) {
        val harness = Harness(Files.createTempDirectory("braincards-autosync-test").toFile())
        try {
            runBlocking { block(harness, harness.open()) }
        } finally {
            harness.close()
        }
    }

    @Test
    fun por_defecto_esta_activado() = withStore { _, store ->
        assertTrue(DataStoreAutoSyncPreference(store).enabled.first())
    }

    @Test
    fun con_el_fichero_de_preferencias_corrupto_se_lee_como_activado_en_vez_de_lanzar() = withStore { harness, store ->
        store.edit { it[themeKey] = "DARK" } // fuerza que el fichero exista
        harness.close()
        harness.dir.mkdirs()
        harness.file.writeBytes(ByteArray(64) { 0xFF.toByte() }) // basura: no es un protobuf valido
        val corrupted = harness.open()

        assertTrue(DataStoreAutoSyncPreference(corrupted).enabled.first())
        assertTrue(corrupted.autoSyncEnabledFlow().first())
    }

    @Test
    fun apagarlo_sobrevive_a_reabrir_el_fichero() = withStore { harness, store ->
        DataStoreAutoSyncPreference(store).setEnabled(false)
        assertFalse(DataStoreAutoSyncPreference(store).enabled.first())

        val reopened = harness.reopen()

        assertFalse(DataStoreAutoSyncPreference(reopened).enabled.first())
    }

    @Test
    fun volver_a_encenderlo_se_guarda() = withStore { harness, store ->
        val preference = DataStoreAutoSyncPreference(store)
        preference.setEnabled(false)

        preference.setEnabled(true)

        assertTrue(preference.enabled.first())
        assertTrue(DataStoreAutoSyncPreference(harness.reopen()).enabled.first())
    }

    @Test
    fun escribirlo_no_toca_las_demas_claves() = withStore { _, store ->
        store.edit { prefs ->
            prefs[themeKey] = "DARK"
            prefs[LAST_SUCCESSFUL_SYNC_KEY] = "2026-11-01T10:00:00Z"
        }

        DataStoreAutoSyncPreference(store).setEnabled(false)

        val prefs = store.data.first()
        assertEquals("DARK", prefs[themeKey])
        assertEquals("2026-11-01T10:00:00Z", prefs[LAST_SUCCESSFUL_SYNC_KEY])
    }

    @Test
    fun quitar_la_clave_con_prefs_menos_igual_la_devuelve_a_activado_y_conserva_el_resto() = withStore { harness, store ->
        store.edit { it[themeKey] = "DARK" }
        val preference = DataStoreAutoSyncPreference(store)
        preference.setEnabled(false)

        store.edit { prefs -> prefs -= AUTO_SYNC_ENABLED_KEY } // lo que hace resetSyncState en una salida confirmada

        assertTrue(preference.enabled.first())
        assertFalse(store.data.first().contains(AUTO_SYNC_ENABLED_KEY))
        val reopened = harness.reopen()
        assertTrue(DataStoreAutoSyncPreference(reopened).enabled.first())
        assertEquals("DARK", reopened.data.first()[themeKey])
    }
}
