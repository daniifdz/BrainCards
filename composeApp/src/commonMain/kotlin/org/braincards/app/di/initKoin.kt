package org.braincards.app.di

import org.braincards.app.data.local.DatabaseFactory
import org.braincards.app.data.local.SettingsDataStoreFactory
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(    databaseFactory: DatabaseFactory,
                 settingsDataStoreFactory: SettingsDataStoreFactory,
                 appDeclaration: KoinAppDeclaration = {}
) = startKoin {
    appDeclaration()
    modules(appModule(databaseFactory.create(), settingsDataStoreFactory.create()))
}