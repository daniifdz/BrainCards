package org.braincards.app.presentation.sync

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.usecase.Sync.SyncResult
import org.braincards.app.presentation.screens.auth.SyncUserMessage
import org.braincards.app.presentation.screens.auth.toSyncUserMessage

/**
 * Indicador de sync de la UI (la píldora de Home y la fila de Ajustes): 9 estados (Error cuenta por 4) más [Unknown], que no
 * se dibuja. Silencioso: nunca lleva textos.
 */
sealed interface SyncIndicator {
    data object Syncing : SyncIndicator
    data class Synced(val at: Instant) : SyncIndicator
    data object NeverSynced : SyncIndicator
    data object Offline : SyncIndicator
    data class Error(val kind: SyncErrorKind) : SyncIndicator
    data object Guest : SyncIndicator

    /** Aun no se ha terminado de leer la ultima sync correcta: no se puede decir "Nunca" ni "Sincronizado". Oculta. */
    data object Unknown : SyncIndicator
}

/**
 * Funcion pura. Prioridad:
 *  1. sin sesion -> [SyncIndicator.Guest]
 *  2. sesion sin confirmar o sin conectividad -> [SyncIndicator.Offline] (aunque el ultimo resultado fuera error)
 *  3. sincronizando -> [SyncIndicator.Syncing]
 *  4. el ultimo sync fallo -> [SyncIndicator.Error] (un fallo de red del pull es [SyncIndicator.Offline])
 *  5. lectura de [lastSuccessfulSyncAt] sin terminar ([lastSuccessfulSyncAtLoaded] = false) -> [SyncIndicator.Unknown]
 *  6. sin ninguna sync correcta -> [SyncIndicator.NeverSynced]; si no, [SyncIndicator.Synced]
 *
 * Las reglas 1-4 no esperan a la lectura. [AuthState.Loading] se trata como sin sesion (igual que deriveSyncStatus);
 * es el ViewModel quien decide no mostrarlo.
 */
fun deriveSyncIndicator(
    auth: AuthState,
    isOnline: Boolean,
    isSyncing: Boolean,
    outcome: SyncOutcome?,
    lastSuccessfulSyncAt: Instant?,
    lastSuccessfulSyncAtLoaded: Boolean
): SyncIndicator {
    if (auth !is AuthState.Authenticated) return SyncIndicator.Guest
    if (!auth.sessionConfirmed || !isOnline) return SyncIndicator.Offline
    if (isSyncing) return SyncIndicator.Syncing
    outcome?.toFailureIndicator()?.let { return it }
    if (!lastSuccessfulSyncAtLoaded) return SyncIndicator.Unknown
    return if (lastSuccessfulSyncAt == null) SyncIndicator.NeverSynced else SyncIndicator.Synced(lastSuccessfulSyncAt)
}

// null = el ultimo sync no fallo (o su resultado no cambia el estado). Reutiliza la clasificacion del sync manual.
private fun SyncOutcome.toFailureIndicator(): SyncIndicator? {
    val r: SyncResult = result ?: return SyncIndicator.Error(SyncErrorKind.FAILED)
    return when (r.toSyncUserMessage()) {
        SyncUserMessage.NotAuthenticated, SyncUserMessage.Synced, SyncUserMessage.ServerOutdated -> null
        SyncUserMessage.PullNetwork -> SyncIndicator.Offline
        SyncUserMessage.PullRejected -> SyncIndicator.Error(SyncErrorKind.REJECTED)
        SyncUserMessage.PullLocal -> SyncIndicator.Error(SyncErrorKind.LOCAL)
        is SyncUserMessage.PushPartial -> SyncIndicator.Error(SyncErrorKind.PUSH_PARTIAL)
        SyncUserMessage.SyncFailed -> SyncIndicator.Error(SyncErrorKind.FAILED)
    }
}
