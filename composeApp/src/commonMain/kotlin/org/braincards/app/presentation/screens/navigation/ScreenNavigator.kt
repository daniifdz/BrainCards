package org.braincards.app.presentation.screens.navigation

import kotlinx.serialization.Serializable
import org.braincards.app.domain.models.StudyScope

@Serializable
sealed class Screen {
    @Serializable
    data object Home : Screen()
    @Serializable
    data object DecksList : Screen()
    @Serializable
    data class FlashcardsList(val deckId: String) : Screen()
    @Serializable
    data class StudySession(val deckId: String? = null) : Screen()
    @Serializable

    data object Settings : Screen()
    @Serializable

    data object Stats : Screen()
    @Serializable
    data object Auth : Screen()
    @Serializable
    data object ForgotPassword : Screen()
}