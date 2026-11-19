package org.braincards.app.presentation.sync

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/** «Última vez»: «Hoy 14:32», «Ayer 14:32» o «3 oct 14:32». El texto lo pone la UI (recursos). */
sealed interface LastSyncLabel {
    data class Today(val time: String) : LastSyncLabel
    data class Yesterday(val time: String) : LastSyncLabel
    data class Date(val day: Int, val month: Int, val time: String) : LastSyncLabel
}

/**
 * Compara el DIA CIVIL en [zone] (no 24 h): 23:50 de ayer visto a las 00:10 de hoy es "Ayer". Un instante futuro
 * (reloj desajustado) cuenta como "Hoy".
 */
fun formatLastSync(at: Instant, now: Instant, zone: TimeZone): LastSyncLabel {
    val local = at.toLocalDateTime(zone)
    val time = local.hour.toString().padStart(2, '0') + ":" + local.minute.toString().padStart(2, '0')
    return when (local.date.daysUntil(now.toLocalDateTime(zone).date)) {
        in Int.MIN_VALUE..0 -> LastSyncLabel.Today(time)
        1 -> LastSyncLabel.Yesterday(time)
        else -> LastSyncLabel.Date(local.dayOfMonth, local.monthNumber, time)
    }
}

enum class SyncDetailState { Syncing, Synced, NeverSynced, Offline, ErrorLocal, ErrorRejected, ErrorPushPartial, ErrorFailed }

/**
 * Detalle de la fila de sync de Ajustes. [lastSync] solo en Synced; [pending] = null donde no se muestra el contador
 * (Sincronizando, Nunca sincronizado y Sincronizado sin cambios pendientes); [canRetry] en los 4 errores.
 */
data class SyncDetail(
    val state: SyncDetailState,
    val lastSync: LastSyncLabel? = null,
    val pending: Int? = null,
    val canRetry: Boolean = false
)

/** null = no se dibuja la seccion (Invitado y Unknown). */
fun syncDetail(
    indicator: SyncIndicator?,
    pendingChanges: Int,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault()
): SyncDetail? = when (indicator) {
    null, SyncIndicator.Guest, SyncIndicator.Unknown -> null
    SyncIndicator.Syncing -> SyncDetail(SyncDetailState.Syncing)
    is SyncIndicator.Synced -> SyncDetail(
        SyncDetailState.Synced,
        lastSync = formatLastSync(indicator.at, now, zone),
        pending = pendingChanges.takeIf { it > 0 }
    )
    SyncIndicator.NeverSynced -> SyncDetail(SyncDetailState.NeverSynced)
    SyncIndicator.Offline -> SyncDetail(SyncDetailState.Offline, pending = pendingChanges)
    is SyncIndicator.Error -> SyncDetail(
        state = when (indicator.kind) {
            SyncErrorKind.LOCAL -> SyncDetailState.ErrorLocal
            SyncErrorKind.REJECTED -> SyncDetailState.ErrorRejected
            SyncErrorKind.PUSH_PARTIAL -> SyncDetailState.ErrorPushPartial
            SyncErrorKind.FAILED -> SyncDetailState.ErrorFailed
        },
        pending = pendingChanges,
        canRetry = true
    )
}
