package org.braincards.app.domain.models

/**
 * Cómo se estudia.
 * - [Scheduled]: sesión normal. Solo las tarjetas pendientes según SM-2, y cada repaso se guarda (`card_scheduling` y
 *   `reviewlog`).
 * - [Free]: repaso libre. Todas las tarjetas del origen en orden aleatorio ([seed] lo fija: la misma semilla da el mismo
 *   orden) y nunca se guarda nada. La semilla viaja en la ruta de navegación, así que un reinicio del proceso reproduce el
 *   mismo orden.
 */
sealed interface StudyMode {
    data object Scheduled : StudyMode
    data class Free(val seed: Long) : StudyMode
}

/** Modo a partir de los argumentos de la ruta. Por defecto (`freeReview = false`) SIEMPRE es la sesion normal. */
fun studyModeFor(freeReview: Boolean, seed: Long): StudyMode = if (freeReview) StudyMode.Free(seed) else StudyMode.Scheduled
