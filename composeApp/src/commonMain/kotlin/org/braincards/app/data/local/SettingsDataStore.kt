package org.braincards.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

internal const val SETTINGS_DATASTORE_FILE_NAME = "braincards.preferences_pb"

// Cada plataforma debe proveer el DataStore de preferencias (tema, etc.).
expect class SettingsDataStoreFactory {
    fun create(): DataStore<Preferences>
}
