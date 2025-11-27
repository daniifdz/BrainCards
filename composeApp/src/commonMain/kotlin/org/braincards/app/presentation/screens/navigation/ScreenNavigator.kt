package org.braincards.app.presentation.screens.navigation


sealed class Screen {
    data object Home : Screen()
    data object DecksList : Screen()
    data class FlashcardsList(val deckId: Int) : Screen()
    data class StudySession(val deckId: Int) : Screen()

    data object Settings : Screen()

    data object Stats : Screen()
}