package org.braincards.app.presentation.screens.sample

import org.braincards.app.presentation.screens.decks.DecksUiState
import org.braincards.app.presentation.screens.home.HomeUiState

/**
 * ¿Se ofrece «Crear mazo de ejemplo» en Home? Solo en el primer uso ([HomeUiState.NoDecks]): 0 mazos vivos (los borrados con
 * tombstone no cuentan). Con cualquier mazo, aunque no tenga tarjetas, no se ofrece.
 */
fun homeShowsSampleDeck(state: HomeUiState): Boolean = state is HomeUiState.NoDecks

/** ¿Se ofrece «Crear mazo de ejemplo» en la Lista de mazos? Solo en el estado vacío ([DecksUiState.Empty]): 0 mazos vivos. */
fun decksShowSampleDeck(state: DecksUiState): Boolean = state is DecksUiState.Empty
