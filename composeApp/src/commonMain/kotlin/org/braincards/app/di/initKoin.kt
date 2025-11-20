package org.braincards.app.di

import org.braincards.app.data.local.DatabaseFactory
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(    databaseFactory: DatabaseFactory,
                 appDeclaration: KoinAppDeclaration = {}
) = startKoin {
    appDeclaration()
    modules(appModule(databaseFactory.create()))
}