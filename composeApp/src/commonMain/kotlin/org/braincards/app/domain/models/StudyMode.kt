package org.braincards.app.domain.models

/**
 * Como se estudia (paso 14, Repaso libre).
 *  - [Scheduled]: la sesion normal: solo las tarjetas pendientes segun SM-2, y cada repaso SE GUARDA (`card_scheduling` y `reviewlog`).
 *  - [Free]: repaso libre: TODAS las tarjetas del origen en orden aleatorio ([seed] lo fija: la misma semilla da el mismo orden) y
 *    NUNCA se guarda nada. La semilla viaja en la ruta de navegacion, asi un reinicio del proceso reproduce el mismo orden.
 */
sealed interface StudyMode {
    data object Scheduled : StudyMode
    data class Free(val seed: Long) : StudyMode
}

/** Modo a partir de los argumentos de la ruta. Por defecto (`freeReview = false`) SIEMPRE es la sesion normal. */
fun studyModeFor(freeReview: Boolean, seed: Long): StudyMode = if (freeReview) StudyMode.Free(seed) else StudyMode.Scheduled
