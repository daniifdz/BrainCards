package org.braincards.app.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.braincards.app.domain.repository.AutoSyncPreference
import org.braincards.app.presentation.sync.AutoSyncController
import org.braincards.app.presentation.sync.SyncCoordinator
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Sincronización automática: una única instancia de [AutoSyncController] para todo el proceso (single, creada al arrancar
 * Koin). Vive en el contenedor de Koin, no en la Activity ni en un ViewModel, así que rotar o recrear la Activity no la
 * reinicia. Se para al cerrar Koin.
 *
 * Necesita de otros módulos: SyncCoordinator, AuthRepository, PendingChangesObserver, AutoSyncPauseGate y los monitores de
 * plataforma (ForegroundMonitor y ConnectivityMonitor, que entran por initKoin(appDeclaration)).
 */
fun autoSyncModule() = module {
    single(createdAtStart = true) {
        AutoSyncController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            timeSource = TimeSource.Monotonic,
            random = Random.Default,
            host = get<SyncCoordinator>(),
            authRepository = get(),
            pendingObserver = get(),
            pauseGate = get(),
            foregroundMonitor = get(),
            connectivityMonitor = get(),
            autoSyncEnabled = get<AutoSyncPreference>().enabled
        ).also { it.start() }
    } onClose { it?.stop() }
}
