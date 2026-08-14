package org.braincards.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory

actual class SettingsDataStoreFactory(
    private val context: Context
) {
    actual fun create(): DataStore<Preferences> {
        val appContext = context.applicationContext
        return PreferenceDataStoreFactory.create(
            produceFile = { appContext.filesDir.resolve(SETTINGS_DATASTORE_FILE_NAME) }
        )
    }
}
