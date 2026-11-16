package org.braincards.app.presentation.screens.settings

import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.braincards.app.data.fakes.FakeSettingsDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Lectura del tema guardado: IOException al leer y valor guardado que ya no existe en el enum. */
class ThemeVMTest {

    private val key = stringPreferencesKey("app_theme")

    @Test
    fun el_tema_guardado_se_lee_tal_cual() = runTest {
        val store = FakeSettingsDataStore(preferencesOf(key to "DARK"))
        assertEquals(AppTheme.DARK, store.themeFlow().first())
    }

    @Test
    fun sin_valor_guardado_es_SYSTEM() = runTest {
        assertEquals(AppTheme.SYSTEM, FakeSettingsDataStore().themeFlow().first())
    }

    @Test
    fun si_leer_el_tema_lanza_IOException_se_usa_SYSTEM() = runTest {
        val store = FakeSettingsDataStore(preferencesOf(key to "DARK"))
        store.readFailure = IOException("preferencias corruptas")
        assertEquals(AppTheme.SYSTEM, store.themeFlow().first())
    }

    @Test
    fun una_excepcion_que_no_es_IOException_se_propaga() = runTest {
        val store = FakeSettingsDataStore()
        store.readFailure = IllegalStateException("otro fallo")
        assertFailsWith<IllegalStateException> { store.themeFlow().first() }
    }

    @Test
    fun un_valor_guardado_que_ya_no_existe_en_el_enum_es_SYSTEM() = runTest {
        val store = FakeSettingsDataStore(preferencesOf(key to "SEPIA"))
        assertEquals(AppTheme.SYSTEM, store.themeFlow().first())
    }

    @Test
    fun parseTheme_tabla() {
        assertEquals(AppTheme.LIGHT, parseTheme("LIGHT"))
        assertEquals(AppTheme.SYSTEM, parseTheme(null))
        assertEquals(AppTheme.SYSTEM, parseTheme(""))
        assertEquals(AppTheme.SYSTEM, parseTheme("dark")) // distingue mayusculas: lo escribe siempre name
    }
}
