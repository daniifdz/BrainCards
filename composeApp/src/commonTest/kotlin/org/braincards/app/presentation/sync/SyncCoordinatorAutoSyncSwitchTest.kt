package org.braincards.app.presentation.sync

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.braincards.app.data.local.AUTO_SYNC_ENABLED_KEY
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.data.local.DataStoreAutoSyncPreference
import org.braincards.app.data.local.LAST_SUCCESSFUL_SYNC_KEY
import org.braincards.app.data.local.PULL_WATERMARK_KEY
import org.braincards.app.data.local.SYNC_PREFS_KEYS
import org.braincards.app.domain.models.AuthState
import org.braincards.app.presentation.screens.auth.AuthVmEnv
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Interruptor de sincronizacion automatica en el SyncCoordinator: el sync automatico se omite con DISABLED (dentro del
 * Mutex), el manual no depende de el, y la clave se reinicia (`prefs -= KEY`) SOLO en salidas confirmadas del usuario
 * (cerrar sesion, eliminar cuenta): nunca con SessionCloseFailed, WipeFailed ni un fallo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorAutoSyncSwitchTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val themeKey = stringPreferencesKey("app_theme")
    private val confirmed = AuthState.Authenticated("user-1", "a@b.co", sessionConfirmed = true)
    private val unconfirmed = AuthState.Authenticated("user-1", "a@b.co", sessionConfirmed = false)

    /** Interruptor apagado + app_theme + las dos claves de sync, para ver que se borra y que no. */
    private suspend fun AuthVmEnv.seed() {
        dataStore.edit { prefs ->
            prefs[AUTO_SYNC_ENABLED_KEY] = false
            prefs[themeKey] = "DARK"
            prefs[LAST_SUCCESSFUL_SYNC_KEY] = "2026-11-01T10:00:00Z"
            prefs[PULL_WATERMARK_KEY] = "{\"user\":\"u\"}"
        }
    }

    private suspend fun AuthVmEnv.prefs() = dataStore.data.first()

    private suspend fun AuthVmEnv.assertSwitchKept() {
        assertEquals(false, prefs()[AUTO_SYNC_ENABLED_KEY], "el interruptor sigue apagado")
        assertEquals("DARK", prefs()[themeKey])
    }

    private suspend fun AuthVmEnv.assertSwitchReset() {
        assertFalse(prefs().contains(AUTO_SYNC_ENABLED_KEY), "la clave se borra (prefs -= KEY): vuelve a activado")
        assertEquals("DARK", prefs()[themeKey], "app_theme se conserva: nunca clear()")
    }

    // --- el sync automatico se omite; el manual no --------------------------------------------------------------------

    @Test
    fun con_el_interruptor_apagado_el_sync_automatico_se_omite_con_DISABLED() = runTest {
        val env = AuthVmEnv(confirmed)
        env.seed()

        val outcome = env.coordinator.requestAutoSync()

        assertEquals(AutoSyncOutcome.Skipped(AutoSyncSkipReason.DISABLED), outcome)
        assertNull(env.coordinator.lastOutcome.value, "no se llego a sincronizar")
    }

    @Test
    fun sin_la_clave_el_interruptor_esta_activado_y_el_sync_automatico_corre() = runTest {
        val env = AuthVmEnv(confirmed)

        assertIs<AutoSyncOutcome.Ran>(env.coordinator.requestAutoSync())
    }

    @Test
    fun con_el_interruptor_apagado_el_sync_manual_funciona_y_no_lo_enciende() = runTest {
        val env = AuthVmEnv(confirmed)
        env.seed()

        env.coordinator.syncNow()

        assertTrue(env.coordinator.lastOutcome.value != null, "el manual se ejecuto")
        assertEquals(false, env.prefs()[AUTO_SYNC_ENABLED_KEY])
    }

    // --- DataStore ilegible: el valor por defecto (activado), igual en preferencia y coordinador ---------------------------

    @Test
    fun si_DataStore_lanza_IOException_al_leer_el_interruptor_el_sync_automatico_corre_como_activado() = runTest {
        val env = AuthVmEnv(confirmed)
        env.dataStore.readFailure = IOException("preferencias corruptas")

        assertIs<AutoSyncOutcome.Ran>(env.coordinator.requestAutoSync())
    }

    @Test
    fun la_preferencia_lee_activado_si_DataStore_lanza_IOException() = runTest {
        val store = FakeSettingsDataStore()
        store.readFailure = IOException("preferencias corruptas")

        assertTrue(DataStoreAutoSyncPreference(store).enabled.first())
    }

    @Test
    fun una_excepcion_que_no_es_IOException_no_se_traga_ni_en_la_preferencia_ni_en_el_coordinador() = runTest {
        val store = FakeSettingsDataStore()
        store.readFailure = IllegalStateException("un bug, no un fallo de disco")
        assertFailsWith<IllegalStateException> { DataStoreAutoSyncPreference(store).enabled.first() }

        val env = AuthVmEnv(confirmed)
        env.dataStore.readFailure = IllegalStateException("un bug, no un fallo de disco")
        assertFailsWith<IllegalStateException> { env.coordinator.requestAutoSync() }
    }

    // --- reinicio solo en salidas confirmadas ---------------------------------------------------------------------------

    @Test
    fun resetSyncState_con_SignedOut_reinicia_el_interruptor_y_borra_las_claves_de_sync() = runTest {
        val env = AuthVmEnv()
        env.seed()

        env.coordinator.resetSyncState(SessionExitReason.SignedOut)

        env.assertSwitchReset()
        assertFalse(env.prefs().contains(LAST_SUCCESSFUL_SYNC_KEY))
        assertFalse(env.prefs().contains(PULL_WATERMARK_KEY))
    }

    @Test
    fun resetSyncState_con_AccountDeleted_reinicia_el_interruptor() = runTest {
        val env = AuthVmEnv()
        env.seed()

        env.coordinator.resetSyncState(SessionExitReason.AccountDeleted)

        env.assertSwitchReset()
    }

    @Test
    fun resetSyncState_sin_motivo_NO_toca_el_interruptor_pero_si_borra_las_claves_de_sync() = runTest {
        val env = AuthVmEnv()
        env.seed()

        env.coordinator.resetSyncState()

        env.assertSwitchKept()
        assertFalse(env.prefs().contains(LAST_SUCCESSFUL_SYNC_KEY))
        assertFalse(env.prefs().contains(PULL_WATERMARK_KEY))
    }

    @Test
    fun un_cierre_de_sesion_completo_reinicia_el_interruptor() = runTest {
        val env = AuthVmEnv(unconfirmed)
        env.seed()

        env.vm.requestSignOut()

        env.assertSwitchReset()
    }

    @Test
    fun eliminar_la_cuenta_con_exito_reinicia_el_interruptor() = runTest {
        val env = AuthVmEnv(confirmed)
        env.seed()

        env.vm.deleteAccount()

        env.assertSwitchReset()
    }

    @Test
    fun SessionCloseFailed_no_toca_el_interruptor() = runTest {
        val env = AuthVmEnv(unconfirmed)
        env.seed()
        env.auth.signOutFailure = RuntimeException("sin red")
        env.auth.clearLocalSessionFailure = RuntimeException("sin acceso a la sesion")

        env.vm.requestSignOut()

        assertEquals(1, env.coordinator.userSessionExitCount.value, "el reset de sync si ocurrio")
        env.assertSwitchKept()
    }

    @Test
    fun un_borrado_local_fallido_no_toca_el_interruptor() = runTest {
        val env = AuthVmEnv(unconfirmed)
        env.seed()
        env.local.wipeFailure = IllegalStateException("disco lleno")

        env.vm.requestSignOut()

        env.assertSwitchKept()
    }

    @Test
    fun un_borrado_de_cuenta_fallido_no_toca_el_interruptor() = runTest {
        val env = AuthVmEnv(confirmed)
        env.seed()
        env.auth.deleteAccountFailure = IllegalStateException("x")

        env.vm.deleteAccount()

        env.assertSwitchKept()
    }

    @Test
    fun reiniciar_sin_la_clave_presente_es_inofensivo() = runTest {
        val env = AuthVmEnv()
        env.dataStore.edit { it[themeKey] = "DARK" }

        env.coordinator.resetSyncState(SessionExitReason.SignedOut)

        assertFalse(env.prefs().contains(AUTO_SYNC_ENABLED_KEY))
        assertEquals("DARK", env.prefs()[themeKey])
    }

    @Test
    fun la_clave_no_forma_parte_de_las_claves_de_sync() {
        // Si estuviera en SYNC_PREFS_KEYS, SessionCloseFailed (resetSyncState sin motivo) tambien la borraria.
        assertFalse(SYNC_PREFS_KEYS.contains(AUTO_SYNC_ENABLED_KEY))
    }
}
