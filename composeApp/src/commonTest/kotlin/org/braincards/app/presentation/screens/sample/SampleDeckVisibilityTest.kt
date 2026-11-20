package org.braincards.app.presentation.screens.sample

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.DeckSummary
import org.braincards.app.presentation.screens.decks.DecksUiState
import org.braincards.app.presentation.screens.home.HomeUiState
import org.braincards.app.presentation.screens.home.homeDeckStats
import org.braincards.app.presentation.screens.home.selectHomeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SampleDeckVisibilityTest {

    private val t = Instant.fromEpochMilliseconds(1_000)
    private fun summary(deleted: Boolean, cards: Int = 0) =
        DeckSummary(Deck("d", "Mazo", t, t, null, deletedAt = if (deleted) t else null), cards)

    @Test
    fun home_solo_lo_ofrece_en_el_primer_uso() {
        assertTrue(homeShowsSampleDeck(HomeUiState.NoDecks))
        listOf(HomeUiState.Loading, HomeUiState.Error, HomeUiState.NoCards, HomeUiState.Empty, HomeUiState.Content(3)).forEach {
            assertFalse(homeShowsSampleDeck(it), "no debe ofrecerse en $it")
        }
    }

    @Test
    fun la_lista_de_mazos_solo_lo_ofrece_cuando_esta_vacia() {
        assertTrue(decksShowSampleDeck(DecksUiState.Empty))
        listOf(DecksUiState.Loading, DecksUiState.Error, DecksUiState.NoResults("x"), DecksUiState.Content(emptyList())).forEach {
            assertFalse(decksShowSampleDeck(it), "no debe ofrecerse en $it")
        }
    }

    @Test
    fun con_cero_mazos_vivos_se_ofrece_y_un_mazo_borrado_no_cuenta() {
        val onlyDeleted = selectHomeState(homeDeckStats(listOf(summary(deleted = true, cards = 4))), dueCount = 0)
        assertEquals(HomeUiState.NoDecks, onlyDeleted)
        assertTrue(homeShowsSampleDeck(onlyDeleted))
    }

    @Test
    fun cualquier_mazo_vivo_lo_oculta_tenga_o_no_tarjetas() {
        val withoutCards = selectHomeState(homeDeckStats(listOf(summary(deleted = false, cards = 0))), dueCount = 0)
        val withCards = selectHomeState(homeDeckStats(listOf(summary(deleted = false, cards = 3))), dueCount = 2)
        assertFalse(homeShowsSampleDeck(withoutCards))
        assertFalse(homeShowsSampleDeck(withCards))
    }

    @Test
    fun el_mazo_borrado_mas_uno_vivo_lo_oculta() {
        val state = selectHomeState(homeDeckStats(listOf(summary(deleted = true), summary(deleted = false))), dueCount = 0)
        assertFalse(homeShowsSampleDeck(state))
    }
}
