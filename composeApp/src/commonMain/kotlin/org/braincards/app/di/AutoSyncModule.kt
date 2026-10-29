package org.braincards.app.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.braincards.app.presentation.sync.AutoSyncController
import org.braincards.app.presentation.sync.SyncCoordinator
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Sincronizacion automatica: UNA instancia del [AutoSyncController] para todo el proceso (single, creada al arrancar
 * Koin y arrancada en el acto). Vive en el contenedor de Koin, no en la Activity ni en un ViewModel, asi que ni
 * rotar la pantalla ni recrear la Activity la reinician. Scope propio a nivel de proceso; se para al cerrar Koin.
 *
 * Necesita, de otros modulos: SyncCoordinator, AuthRepository, PendingChangesObserver, AutoSyncPauseGate y los
 * monitores de PLATAFORMA (ForegroundMonitor y ConnectivityMonitor: androidSyncModule / desktopSyncModule, que
 * entran por initKoin(appDeclaration)).
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
            connectivityMonitor = get()
        ).also { it.start() }
    } onClose { it?.stop() }
}
