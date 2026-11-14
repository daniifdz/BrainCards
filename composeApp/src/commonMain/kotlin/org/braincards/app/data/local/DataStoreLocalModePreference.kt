package org.braincards.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.braincards.app.domain.repository.LocalModePreference

/**
 * Clave del flag «modo local elegido» en el DataStore compartido (braincards.preferences_pb). NO es estado de sync
 * (no va en SYNC_PREFS_KEYS): lo gestiona solo el arranque (AppEntryViewModel).
 */
val LOCAL_MODE_CHOSEN_KEY = booleanPreferencesKey("local_mode_chosen")

/** [LocalModePreference] sobre el DataStore de preferencias. Solo toca SU clave: nunca `clear()`. */
class DataStoreLocalModePreference(private val dataStore: DataStore<Preferences>) : LocalModePreference {

    override val chosen: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[LOCAL_MODE_CHOSEN_KEY] ?: false }
        .distinctUntilChanged()

    override suspend fun setChosen(value: Boolean) {
        dataStore.edit { prefs ->
            if (value) prefs[LOCAL_MODE_CHOSEN_KEY] = true else prefs -= LOCAL_MODE_CHOSEN_KEY
        }
    }
}
