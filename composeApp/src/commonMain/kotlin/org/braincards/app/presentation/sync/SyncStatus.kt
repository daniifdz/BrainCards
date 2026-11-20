package org.braincards.app.presentation.sync

import kotlinx.datetime.Instant
import org.braincards.app.domain.usecase.Sync.SyncResult

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
