package org.braincards.app.domain.models

/**
 * Resultado de aplicar localmente una tabla del pull ([applied] filas escritas) junto con las
 * filas que se DESCARTARON sin abortar la tabla ([discarded]): las que referencian un padre que
 * no existe en la BD local (p. ej. una flashcard cuyo deck pertenece a otra cuenta).
 */
data class PullApplyReport(val applied: Int, val discarded: Int = 0)
