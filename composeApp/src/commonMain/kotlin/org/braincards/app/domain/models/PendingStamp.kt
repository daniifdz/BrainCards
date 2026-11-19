package org.braincards.app.domain.models

/**
 * "Huella" de los cambios locales pendientes de subir: cuantos son y el `updatedAt` (epoch ms) del mas
 * reciente. Cambia cuando entra un cambio nuevo Y cuando se edita una fila que ya estaba pendiente (el
 * conteo no cambia pero el maximo si); vuelve a (0, 0) cuando todo esta subido.
 */
data class PendingStamp(
    val count: Int,
    val newestUpdatedAtMillis: Long
)
