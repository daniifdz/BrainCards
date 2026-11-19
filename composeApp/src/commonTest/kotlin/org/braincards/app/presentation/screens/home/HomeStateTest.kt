package org.braincards.app.presentation.screens.home

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.DeckSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Selección pura del estado de Home: NoDecks -> NoCards -> Empty -> Content. El total de tarjetas sale de la suma de
 * `cardCount` de los mazos vivos; «pendientes» solo decide entre Empty y Content.
 */
class HomeStateTest {

    private val t0 = Instant.fromEpochMilliseconds(1_000)

    private fun summary(id: String, cards: Int, deleted: Boolean = false) = DeckSummary(
        deck = Deck(id, id, t0, t0, null, deletedAt = if (deleted) Instant.fromEpochMilliseconds(5_000) else null),
        cardCount = cards
    )

    private fun state(summaries: List<DeckSummary>, due: Int) = selectHomeState(homeDeckStats(summaries), due)

    @Test
    fun cero_mazos_es_primer_uso() {
        assertEquals(HomeUiState.NoDecks, state(emptyList(), due = 0))
    }

    @Test
    fun mazos_sin_ninguna_tarjeta_es_NoCards() {
        assertEquals(HomeUiState.NoCards, state(listOf(summary("a", 0)), due = 0))
        assertEquals(HomeUiState.NoCards, state(listOf(summary("a", 0), summary("b", 0)), due = 0))
    }

    // ...y este: con tarjetas pero 0 pendientes NO es NoCards, es "Todo al dia".
    @Test
    fun mazos_con_tarjetas_pero_cero_pendientes_es_Empty_todo_al_dia() {
        assertEquals(HomeUiState.Empty, state(listOf(summary("a", 5)), due = 0))
    }

    @Test
    fun tarjetas_pendientes_es_Content_con_su_recuento() {
        assertEquals(HomeUiState.Content(3), state(listOf(summary("a", 5)), due = 3))
    }

    @Test
    fun el_total_se_suma_entre_mazos_basta_con_que_uno_tenga_tarjetas() {
        assertEquals(HomeUiState.Empty, state(listOf(summary("vacio", 0), summary("con", 2)), due = 0))
        assertEquals(HomeUiState.Content(1), state(listOf(summary("vacio", 0), summary("con", 2)), due = 1))
    }

    @Test
    fun el_primer_uso_manda_sobre_sin_tarjetas() {
        assertEquals(HomeUiState.NoDecks, state(emptyList(), due = 0))
        assertEquals(HomeUiState.NoDecks, state(emptyList(), due = 7))
    }

    @Test
    fun sin_tarjetas_manda_sobre_al_dia_y_sobre_el_contenido_aunque_el_contador_diga_otra_cosa() {
        assertEquals(HomeUiState.NoCards, state(listOf(summary("a", 0)), due = 4))
    }

    @Test
    fun un_mazo_borrado_no_cuenta_ni_sus_tarjetas() {
        // Solo hay un mazo y esta borrado (con tarjetas "vivas" colgando): primer uso.
        assertEquals(HomeUiState.NoDecks, state(listOf(summary("borrado", 5, deleted = true)), due = 5))
        // Un mazo borrado con tarjetas + un mazo vivo SIN tarjetas: las del borrado no cuentan -> NoCards.
        assertEquals(
            HomeUiState.NoCards,
            state(listOf(summary("borrado", 5, deleted = true), summary("vivo", 0)), due = 0)
        )
    }

    @Test
    fun un_mazo_cuyas_tarjetas_estan_todas_borradas_llega_con_cardCount_cero_y_es_NoCards() {
        // El recuento (consulta agregada) ya excluye las tarjetas borradas: ver HomeStateRoomTest con Room real.
        assertEquals(HomeUiState.NoCards, state(listOf(summary("a", 0)), due = 0))
    }

    @Test
    fun el_resumen_cuenta_mazos_vivos_y_suma_sus_tarjetas() {
        val stats = homeDeckStats(listOf(summary("a", 2), summary("b", 3), summary("c", 9, deleted = true)))
        assertEquals(HomeDeckStats(deckCount = 2, cardCount = 5), stats)
    }
}
