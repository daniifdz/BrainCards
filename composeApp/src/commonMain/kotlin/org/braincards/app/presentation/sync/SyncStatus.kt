package org.braincards.app.presentation.sync

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.usecase.Sync.SyncResult
import org.braincards.app.presentation.screens.auth.SyncUserMessage
import org.braincards.app.presentation.screens.auth.toSyncUserMessage

/** Estado observable de la sincronizacion para la UI (el rediseño lo mostrara). Silencioso: nunca lleva textos. */
sealed interface SyncStatus {
    data object Syncing : SyncStatus
    data class Synced(val at: Instant) : SyncStatus
    data class Error(val kind: SyncErrorKind) : SyncStatus
    data object Offline : SyncStatus
    data object SignedOut : SyncStatus
}

/** Tipo de fallo de un sync que NO es simplemente "sin conexion". */
enum class SyncErrorKind {
    /** Fallo al guardar lo descargado en este dispositivo. */
    LOCAL,

    /** El servidor rechazo la peticion (4xx: sesion o permisos). */
    REJECTED,

    /** El pull fue bien pero hay filas que no se pudieron subir. */
    PUSH_PARTIAL,

    /** Fallaron el pull y el push a la vez, o una excepcion inesperada del motor. */
    FAILED
}

/**
 * Lo que dejo el ULTIMO sync (manual, completo o automatico). [result] es null si el motor lanzo una excepcion
 * inesperada. [at] es la hora del dispositivo al terminar.
 */
data class SyncOutcome(val result: SyncResult?, val at: Instant)

/** Por que [SyncCoordinator.requestAutoSync] no sincronizo. */
enum class AutoSyncSkipReason {
    NO_SESSION,
    SESSION_UNCONFIRMED,
    PAUSED_SIGN_OUT,
    PAUSED_STUDY,

    /** El usuario apago la sincronizacion automatica (se comprueba DENTRO del Mutex, como la puerta de pausa). */
    DISABLED
}

/** Resultado de [SyncCoordinator.requestAutoSync]: se ejecuto (con su resultado) u "omitido" con su motivo. */
sealed interface AutoSyncOutcome {
    data class Ran(val result: SyncResult) : AutoSyncOutcome
    data class Skipped(val reason: AutoSyncSkipReason) : AutoSyncOutcome
}

/**
 * Estado de sync a partir de la sesion, de si hay un sync en curso, del ultimo resultado y de la ultima
 * sincronizacion correcta persistida. Reutiliza la clasificacion del mensaje del sync manual
 * ([toSyncUserMessage]) para no duplicar su prioridad (LOCAL > REJECTED > red).
 */
fun deriveSyncStatus(
    auth: AuthState,
    isSyncing: Boolean,
    outcome: SyncOutcome?,
    lastSuccessfulSyncAt: Instant?
): SyncStatus = when (auth) {
    AuthState.Loading, AuthState.NotAuthenticated -> SyncStatus.SignedOut
    is AuthState.Authenticated -> when {
        isSyncing -> SyncStatus.Syncing
        // Sesion guardada sin confirmar (arranque en frio sin red): aun no se hablo con el servidor.
        !auth.sessionConfirmed -> SyncStatus.Offline
        outcome != null -> outcome.toSyncStatus()
        lastSuccessfulSyncAt != null -> SyncStatus.Synced(lastSuccessfulSyncAt)
        // Sesion confirmada y ningun sync todavia: el de arranque esta al caer.
        else -> SyncStatus.Syncing
    }
}

private fun SyncOutcome.toSyncStatus(): SyncStatus {
    val r = result ?: return SyncStatus.Error(SyncErrorKind.FAILED)
    return when (r.toSyncUserMessage()) {
        SyncUserMessage.NotAuthenticated -> SyncStatus.SignedOut
        SyncUserMessage.Synced, SyncUserMessage.ServerOutdated -> SyncStatus.Synced(at)
        SyncUserMessage.PullNetwork -> SyncStatus.Offline
        SyncUserMessage.PullRejected -> SyncStatus.Error(SyncErrorKind.REJECTED)
        SyncUserMessage.PullLocal -> SyncStatus.Error(SyncErrorKind.LOCAL)
        is SyncUserMessage.PushPartial -> SyncStatus.Error(SyncErrorKind.PUSH_PARTIAL)
        SyncUserMessage.SyncFailed -> SyncStatus.Error(SyncErrorKind.FAILED)
    }
}
