package org.braincards.app

import android.app.Application
import org.braincards.app.data.local.DatabaseFactory
import org.braincards.app.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class BraindCardsApp : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin(
            databaseFactory = DatabaseFactory(this)
        ) {
            androidLogger()
            androidContext(this@BraindCardsApp)
        }
    }
}