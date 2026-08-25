package org.braincards.app

import android.app.Application
import org.braincards.app.data.local.DatabaseFactory
import org.braincards.app.data.local.SettingsDataStoreFactory
import org.braincards.app.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class BrainCardsApp : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin(
            databaseFactory = DatabaseFactory(this),
            settingsDataStoreFactory = SettingsDataStoreFactory(this)
        ) {
            androidLogger()
            androidContext(this@BrainCardsApp)
        }
    }
}