package org.braincards.app.domain.models

/**
 * Resultado de aplicar localmente una tabla del pull: [applied] filas escritas y [discarded] filas descartadas sin abortar
 * la tabla (referencian un padre que no existe en la BD local, p. ej. una tarjeta cuyo mazo es de otra cuenta).
 *
 * [discardedIds] son sus ids (`id`, o `card_id` en card_scheduling): el pull incremental los necesita para no avanzar la
 * marca de agua más allá de la primera fila descartada en el orden del servidor. Vale vacío por defecto.
 */
data class PullApplyReport(
    val applied: Int,
    val discarded: Int = 0,
    val discardedIds: List<String> = emptyList()
)
