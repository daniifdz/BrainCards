package org.braincards.app.presentation.screens.navigation

/**
 * Puertas de entrada a la sesión de estudio como decisión pura: qué ruta se abre y si sustituye a la pantalla de estudio actual.
 * - [Study]: «Estudiar» / «Estudiar Todo»: sesión normal (`freeReview = false`, sin semilla).
 * - [FreeReview]: «Repaso libre» desde Contenido de un mazo o Home («Todo al día»): `freeReview = true` con la semilla que genera
 *   el punto de navegación (`Random.nextLong()`): la misma semilla reproduce el mismo orden aunque el proceso muera.
 * - [FreeReviewFromEmpty]: «Repaso libre» desde el estado vacío de Estudio («¡Estamos al día!»): igual que [FreeReview] pero la
 *   sesión libre sustituye a la pantalla actual (`popUpTo<StudySession> { inclusive = true }`): la pila no crece y ✕ / atrás
 *   siguen volviendo al origen (Home o el mazo). Cambiar de modo dentro de la misma ruta no valdría: al rotar la ruta seguiría
 *   diciendo «programado».
 */
enum class StudyEntry { Study, FreeReview, FreeReviewFromEmpty }

data class StudyNavigation(val destination: Screen.StudySession, val replaceCurrentStudy: Boolean)

fun studyNavigation(entry: StudyEntry, deckId: String?, seed: Long): StudyNavigation = when (entry) {
    StudyEntry.Study -> StudyNavigation(Screen.StudySession(deckId = deckId), replaceCurrentStudy = false)
    StudyEntry.FreeReview -> StudyNavigation(Screen.StudySession(deckId = deckId, freeReview = true, seed = seed), replaceCurrentStudy = false)
    StudyEntry.FreeReviewFromEmpty -> StudyNavigation(Screen.StudySession(deckId = deckId, freeReview = true, seed = seed), replaceCurrentStudy = true)
}
