package org.braincards.app.domain.models

/**
 * Resultado de aplicar localmente una tabla del pull ([applied] filas escritas) junto con las
 * filas que se DESCARTARON sin abortar la tabla ([discarded]): las que referencian un padre que
 * no existe en la BD local (p. ej. una flashcard cuyo deck pertenece a otra cuenta).
 *
 * [discardedIds] son los ids de esas filas descartadas (la clave de la tabla: `id`, o `card_id` en
 * card_scheduling). El pull incremental los necesita para no avanzar la marca de agua mas alla de la
 * primera fila descartada en el orden del servidor. Valor por defecto vacio: quien solo informa del
 * contador (o construye el informe a mano) sigue valiendo.
 */
data class PullApplyReport(
    val applied: Int,
    val discarded: Int = 0,
    val discardedIds: List<String> = emptyList()
)
