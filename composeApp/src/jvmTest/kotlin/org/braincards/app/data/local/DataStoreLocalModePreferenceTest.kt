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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El flag «modo local elegido» sobre un DataStore REAL en un FICHERO TEMPORAL del test (nunca el DataStore ni las
 * preferencias del usuario). Borrar el flag quita SOLO su clave (`prefs -= KEY`, nunca clear()): app_theme y las
 * claves de sync se conservan.
 */
class DataStoreLocalModePreferenceTest {

    private val themeKey = stringPreferencesKey("app_theme")

    private class Harness(val dir: File) {
        val file = File(dir, "test.preferences_pb")
        private val scopes = mutableListOf<CoroutineScope>()

        fun open(): DataStore<Preferences> {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
            return PreferenceDataStoreFactory.create(scope = scope) { file }
        }

        // Un DataStore por fichero y proceso: para reabrir hay que cerrar el anterior.
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
        val harness = Harness(Files.createTempDirectory("braincards-localmode-test").toFile())
        try {
            runBlocking { block(harness, harness.open()) }
        } finally {
            harness.close()
        }
    }

    private suspend fun seedOtherKeys(store: DataStore<Preferences>) {
        store.edit { prefs ->
            prefs[themeKey] = "DARK"
            prefs[LAST_SUCCESSFUL_SYNC_KEY] = "2026-11-01T10:00:00Z"
            prefs[PULL_WATERMARK_KEY] = "{\"user\":\"u\"}"
        }
    }

    private suspend fun assertOtherKeysIntact(store: DataStore<Preferences>) {
        val prefs = store.data.first()
        assertEquals("DARK", prefs[themeKey], "app_theme")
        assertEquals("2026-11-01T10:00:00Z", prefs[LAST_SUCCESSFUL_SYNC_KEY], "last_successful_sync_at")
        assertEquals("{\"user\":\"u\"}", prefs[PULL_WATERMARK_KEY], "pull_watermark")
    }

    @Test
    fun por_defecto_no_hay_modo_local_elegido() = withStore { _, store ->
        assertFalse(DataStoreLocalModePreference(store).chosen.first())
    }

    @Test
    fun elegirlo_lo_activa_y_sobrevive_a_reabrir_el_fichero() = withStore { harness, store ->
        DataStoreLocalModePreference(store).setChosen(true)
        assertTrue(DataStoreLocalModePreference(store).chosen.first())

        val reopened = harness.reopen()

        assertTrue(DataStoreLocalModePreference(reopened).chosen.first())
    }

    @Test
    fun borrarlo_quita_solo_su_clave_y_conserva_app_theme_y_las_claves_de_sync() = withStore { harness, store ->
        seedOtherKeys(store)
        val preference = DataStoreLocalModePreference(store)
        preference.setChosen(true)

        preference.setChosen(false)

        val prefs = store.data.first()
        assertFalse(prefs.contains(LOCAL_MODE_CHOSEN_KEY), "la clave desaparece (prefs -= KEY), no queda en false")
        assertNull(prefs[LOCAL_MODE_CHOSEN_KEY])
        assertFalse(preference.chosen.first())
        assertOtherKeysIntact(store)
        // Y en disco: reabriendo el fichero siguen las demas claves.
        assertOtherKeysIntact(harness.reopen())
    }

    @Test
    fun activarlo_no_toca_el_resto_de_claves() = withStore { _, store ->
        seedOtherKeys(store)

        DataStoreLocalModePreference(store).setChosen(true)

        assertOtherKeysIntact(store)
    }

    @Test
    fun borrarlo_cuando_no_existia_es_inofensivo() = withStore { _, store ->
        seedOtherKeys(store)

        DataStoreLocalModePreference(store).setChosen(false)

        assertFalse(store.data.first().contains(LOCAL_MODE_CHOSEN_KEY))
        assertOtherKeysIntact(store)
    }

    @Test
    fun la_clave_no_forma_parte_de_las_claves_de_sync() {
        // resetSyncState borra SYNC_PREFS_KEYS: el flag lo gestiona solo el arranque.
        assertFalse(SYNC_PREFS_KEYS.contains(LOCAL_MODE_CHOSEN_KEY))
    }
}
