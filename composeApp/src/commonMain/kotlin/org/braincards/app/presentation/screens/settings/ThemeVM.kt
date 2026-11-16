package org.braincards.app.presentation.screens.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val THEME_KEY = stringPreferencesKey("app_theme")

/** Valor guardado -> tema. Ausente o que ya no existe en el enum (version nueva/antigua) = [AppTheme.SYSTEM]. */
internal fun parseTheme(stored: String?): AppTheme =
    AppTheme.entries.firstOrNull { it.name == stored } ?: AppTheme.SYSTEM

/**
 * LECTURA UNICA del tema: si DataStore falla al leer (IOException, incluida CorruptionException) se lee como si las
 * preferencias estuvieran vacias (= SYSTEM), mismo patron que `autoSyncEnabledFlow`. Cualquier otra excepcion se propaga.
 */
internal fun DataStore<Preferences>.themeFlow(): Flow<AppTheme> = data
    .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
    .map { prefs -> parseTheme(prefs[THEME_KEY]) }

class ThemeViewModel(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val theme: StateFlow<AppTheme?> = dataStore.themeFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun setTheme(newTheme: AppTheme) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[THEME_KEY] = newTheme.name }
        }
    }
}
