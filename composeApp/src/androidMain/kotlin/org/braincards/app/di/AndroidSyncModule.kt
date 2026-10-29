package org.braincards.app.di

import org.braincards.app.platform.AndroidConnectivityMonitor
import org.braincards.app.platform.AndroidForegroundMonitor
import org.braincards.app.presentation.sync.ConnectivityMonitor
import org.braincards.app.presentation.sync.ForegroundMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * Monitores de la sync automatica en Android. Entra por initKoin(appDeclaration) desde BrainCardsApp.
 *
 * HILO PRINCIPAL: AndroidForegroundMonitor registra su observador en ProcessLifecycleOwner, que solo admite
 * addObserver en el hilo principal (si no, lanza IllegalStateException al instante, no en silencio). Koin crea los
 * singles con createdAtStart dentro de startKoin, y startKoin se llama desde Application.onCreate, que se ejecuta
 * en el hilo principal: por eso es correcto crearlo aqui. No mover estas definiciones a un hilo de fondo.
 */
fun androidSyncModule() = module {
    single<ForegroundMonitor> { AndroidForegroundMonitor() } onClose { (it as? AutoCloseable)?.close() }
    single<ConnectivityMonitor> { AndroidConnectivityMonitor(androidContext()) } onClose { (it as? AutoCloseable)?.close() }
}
