package org.braincards.app.presentation.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Por que la sincronizacion AUTOMATICA esta en pausa. El sync MANUAL nunca se bloquea con la puerta. */
enum class PauseReason {
    /** Cierre de sesion en curso (desde prepareSignOut hasta finishSignOut/abortSignOut). */
    SIGN_OUT,

    /** Sesion de estudio activa: un pull automatico no debe aplicar filas mientras se estudia. */
    STUDY
}

/** Caducidad del motivo SIGN_OUT desde su ultima renovacion: el dialogo de confirmacion no pausa el sync para siempre. */
val SIGN_OUT_PAUSE_TTL: Duration = 5.minutes

/** Caducidad del motivo STUDY POR INACTIVIDAD: se renueva con cada tarjeta puntuada, no es un plazo fijo. */
val STUDY_PAUSE_INACTIVITY_TTL: Duration = 30.minutes

/**
 * Puerta de pausa COMPARTIDA de la sync automatica: quien necesita que no se sincronice sola toma un [Lease] con
 * su [PauseReason] y lo libera al terminar. Recuento de referencias (varios leases del mismo motivo; la puerta
 * queda abierta cuando no queda ninguno activo) y caducidad por inactividad (un lease no renovado en su TTL deja
 * de contar, por si su dueno murio sin liberarlo). Reloj MONOTONICO inyectable.
 *
 * Hilo-segura: el estado es una lista inmutable en un StateFlow modificada solo con update {}. La caducidad se
 * evalua al consultar ([activeReason]); [isPaused] se actualiza en cada acquire/renew/release y en cada consulta.
 */
class AutoSyncPauseGate(
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val ttls: Map<PauseReason, Duration> = mapOf(
        PauseReason.SIGN_OUT to SIGN_OUT_PAUSE_TTL,
        PauseReason.STUDY to STUDY_PAUSE_INACTIVITY_TTL
    )
) {

    /** Una toma de la puerta. [release] es idempotente; [renew] reinicia la cuenta de inactividad. */
    interface Lease {
        val reason: PauseReason
        fun renew()
        fun release()
    }

    private class Entry(val lease: LeaseImpl, val lastActive: TimeMark)

    private val entries = MutableStateFlow<List<Entry>>(emptyList())
    private val _isPaused = MutableStateFlow(false)

    /** true mientras haya algun lease activo. Para que el controlador reaccione a la apertura de la puerta. */
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    fun acquire(reason: PauseReason): Lease {
        val lease = LeaseImpl(reason)
        entries.update { it + Entry(lease, timeSource.markNow()) }
        publish()
        return lease
    }

    /** Motivo que pausa AHORA (SIGN_OUT manda sobre STUDY) o null si la puerta esta abierta. Descarta los caducados. */
    fun activeReason(): PauseReason? {
        val active = entries.value.filter { isActive(it) }
        return PauseReason.entries.firstOrNull { reason -> active.any { it.lease.reason == reason } }
            .also { _isPaused.value = it != null }
    }

    /** Leases activos (no liberados ni caducados) de [reason]. */
    fun activeCount(reason: PauseReason): Int =
        entries.value.count { it.lease.reason == reason && isActive(it) }

    private fun isActive(entry: Entry): Boolean {
        val ttl = ttls.getValue(entry.lease.reason)
        return entry.lastActive.elapsedNow() < ttl
    }

    private fun publish() {
        _isPaused.value = entries.value.any { isActive(it) }
    }

    private inner class LeaseImpl(override val reason: PauseReason) : Lease {
        // Renovar un lease CADUCADO pero no liberado lo reactiva: el dueno sigue vivo (p. ej. se puntua una tarjeta
        // tras 40 min parado); solo release() lo da de baja.
        override fun renew() {
            entries.update { list ->
                if (list.none { it.lease === this }) list
                else list.map { if (it.lease === this) Entry(this, timeSource.markNow()) else it }
            }
            publish()
        }

        override fun release() {
            entries.update { list -> list.filterNot { it.lease === this } }
            publish()
        }
    }
}
