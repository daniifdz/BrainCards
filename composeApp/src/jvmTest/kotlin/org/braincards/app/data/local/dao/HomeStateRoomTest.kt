package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.repository.DeckSummariesObserverImpl
import org.braincards.app.domain.models.CardType
import org.braincards.app.presentation.screens.home.HomeUiState
import org.braincards.app.presentation.screens.home.homeDeckStats
import org.braincards.app.presentation.screens.home.selectHomeState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El estado de Home "mazos sin tarjetas" contra una Room real en memoria: la suma de `cardCount` de DeckSummary solo
 * cuenta tarjetas vivas de mazos vivos, asi que un mazo borrado o una tarjeta borrada no cambian el resultado.
 */
class HomeStateRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var observer: DeckSummariesObserverImpl

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
        observer = DeckSummariesObserverImpl(db.deckDao())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun deck(id: String, deleted: Boolean = false) = db.deckDao().addDeck(
        DeckEntity(id = id, name = id, createdAt = ms(1), updatedAt = ms(1), description = null,
            deletedAt = if (deleted) ms(2) else null)
    )

    private suspend fun card(id: String, deckId: String, deleted: Boolean = false) = db.flashcardDao().addFlashcard(
        FlashcardEntity(id = id, deckId = deckId, question = "Q", answer = "A", cardType = CardType.FLIP,
            createdAt = ms(1), updatedAt = ms(1), deletedAt = if (deleted) ms(2) else null)
    )

    private suspend fun state(due: Int): HomeUiState {
        val summaries = observer.observeDeckSummaries().first()
        return selectHomeState(homeDeckStats(summaries), due)
    }

    @Test
    fun sin_mazos_es_primer_uso() = runBlocking<Unit> {
        assertEquals(HomeUiState.NoDecks, state(due = 0))
    }

    @Test
    fun un_mazo_vivo_sin_tarjetas_es_NoCards() = runBlocking<Unit> {
        deck("a")
        assertEquals(HomeUiState.NoCards, state(due = 0))
    }

    @Test
    fun una_tarjeta_borrada_no_cuenta_un_mazo_con_solo_tarjetas_borradas_es_NoCards() = runBlocking<Unit> {
        deck("a")
        card("c1", "a", deleted = true)
        assertEquals(HomeUiState.NoCards, state(due = 0))
    }

    @Test
    fun con_una_tarjeta_viva_deja_de_ser_NoCards() = runBlocking<Unit> {
        deck("a")
        card("c1", "a")
        card("c2", "a", deleted = true)
        assertEquals(HomeUiState.Empty, state(due = 0))
        assertEquals(HomeUiState.Content(1), state(due = 1))
    }

    @Test
    fun un_mazo_borrado_con_tarjetas_vivas_no_cuenta_y_vuelve_al_primer_uso() = runBlocking<Unit> {
        deck("borrado", deleted = true)
        card("c1", "borrado")
        assertEquals(HomeUiState.NoDecks, state(due = 1))
    }

    @Test
    fun un_mazo_borrado_con_tarjetas_junto_a_un_mazo_vivo_vacio_es_NoCards() = runBlocking<Unit> {
        deck("borrado", deleted = true)
        card("c1", "borrado")
        deck("vivo")
        assertEquals(HomeUiState.NoCards, state(due = 0))
    }
}
