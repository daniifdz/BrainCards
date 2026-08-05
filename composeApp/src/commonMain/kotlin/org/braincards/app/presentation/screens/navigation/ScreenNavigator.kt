package org.braincards.app.presentation.screens.navigation

import org.braincards.app.domain.models.StudyScope


sealed class Screen {
    data object Home : Screen()
    data object DecksList : Screen()
    data class FlashcardsList(val deckId: String) : Screen()
    data class StudySession(val scope: StudyScope) : Screen()

    data object Settings : Screen()

    data object Stats : Screen()
}