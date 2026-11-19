package org.braincards.app.presentation.screens.navigation

/**
 * Puertas de entrada a la sesion de estudio (paso 14c, Repaso libre) como decision PURA: que ruta se abre y si sustituye a la pantalla
 * de estudio actual.
 *  - [Study]: «Estudiar» / «Estudiar Todo»: sesion normal (`freeReview = false`, sin semilla).
 *  - [FreeReview]: «Repaso libre» desde Contenido de un mazo o Home («Todo al dia»): `freeReview = true` con la SEMILLA que genera el punto
 *    de navegacion (`Random.nextLong()`): la misma semilla reproduce el mismo orden aunque el proceso muera.
 *  - [FreeReviewFromEmpty]: «Repaso libre» desde el estado vacio de Estudio («¡Estamos al dia!»): igual que [FreeReview] pero la sesion libre
 *    SUSTITUYE a la pantalla actual (`popUpTo<StudySession> { inclusive = true }`): la pila no crece y ✕ / atras siguen volviendo al ORIGEN
 *    (Home o el mazo). Cambiar de modo dentro de la misma ruta no valdria: al rotar la ruta seguiria diciendo «programado».
 */
enum class StudyEntry { Study, FreeReview, FreeReviewFromEmpty }

data class StudyNavigation(val destination: Screen.StudySession, val replaceCurrentStudy: Boolean)

fun studyNavigation(entry: StudyEntry, deckId: String?, seed: Long): StudyNavigation = when (entry) {
    StudyEntry.Study -> StudyNavigation(Screen.StudySession(deckId = deckId), replaceCurrentStudy = false)
    StudyEntry.FreeReview -> StudyNavigation(Screen.StudySession(deckId = deckId, freeReview = true, seed = seed), replaceCurrentStudy = false)
    StudyEntry.FreeReviewFromEmpty -> StudyNavigation(Screen.StudySession(deckId = deckId, freeReview = true, seed = seed), replaceCurrentStudy = true)
}
