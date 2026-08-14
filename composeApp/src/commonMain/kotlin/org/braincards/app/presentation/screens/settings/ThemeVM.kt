package org.braincards.app.presentation.screens.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val THEME_KEY = stringPreferencesKey("app_theme")

class ThemeViewModel(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val theme: StateFlow<AppTheme> = dataStore.data
        .map { prefs -> prefs[THEME_KEY]?.let { AppTheme.valueOf(it) } ?: AppTheme.SYSTEM }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppTheme.SYSTEM
        )

    fun setTheme(newTheme: AppTheme) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[THEME_KEY] = newTheme.name }
        }
    }
}
