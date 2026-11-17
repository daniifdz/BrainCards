package org.braincards.app.domain.usecase.Flashcards

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateFlashcardUseCaseTest {

    private val repository = FakeFlashcardRepository()
    private val now = Instant.fromEpochMilliseconds(9_000)
    private val useCase = UpdateFlashcardUseCase(repository, object : Clock {
        override fun now(): Instant = now
    })

    private fun card(question: String = "Q", answer: String = "A", topicId: String? = null) = Flashcard(
        id = "c1", deckId = "d1", question = question, answer = answer,
        createdAt = Instant.fromEpochMilliseconds(1_000), updatedAt = Instant.fromEpochMilliseconds(1_000),
        cardType = CardType.FLIP, topicId = topicId, lastSyncedAt = Instant.fromEpochMilliseconds(2_000)
    )

    @Test
    fun un_anverso_en_blanco_lanza_FlashcardFieldException_y_no_escribe() = runBlocking<Unit> {
        val e = assertFailsWith<FlashcardFieldException> { useCase(card(question = " ")) }
        assertEquals(setOf(CardField.Question), e.fields)
        assertTrue(repository.updateFlashcardCalls.isEmpty())
    }

    @Test
    fun un_reverso_en_blanco_lanza_FlashcardFieldException_y_no_escribe() = runBlocking<Unit> {
        val e = assertFailsWith<FlashcardFieldException> { useCase(card(answer = "")) }
        assertEquals(setOf(CardField.Answer), e.fields)
        assertTrue(repository.updateFlashcardCalls.isEmpty())
    }

    // Mutacion U1: si no se fijara updatedAt, este test fallaria (una edicion no se sincronizaria nunca).
    @Test
    fun guardar_fija_updatedAt_al_reloj_y_conserva_el_resto() = runBlocking<Unit> {
        useCase(card(question = "Q2", answer = "A2"))

        val saved = repository.updateFlashcardCalls.single()
        assertEquals(now, saved.updatedAt)
        assertEquals(Instant.fromEpochMilliseconds(1_000), saved.createdAt)
        assertEquals(Instant.fromEpochMilliseconds(2_000), saved.lastSyncedAt)
        assertEquals("Q2", saved.question)
    }

    // Mutacion U1: el caso que importa: mover la tarjeta de tema avanza updatedAt, asi que vuelve a ser pendiente.
    @Test
    fun cambiar_de_tema_avanza_updatedAt_por_encima_de_lastSyncedAt() = runBlocking<Unit> {
        useCase(card(topicId = "t-nuevo"))

        val saved = repository.updateFlashcardCalls.single()
        assertEquals("t-nuevo", saved.topicId)
        assertTrue(saved.updatedAt > saved.lastSyncedAt!!)
    }

    @Test
    fun se_puede_sacar_una_tarjeta_de_su_tema_con_topicId_null() = runBlocking<Unit> {
        useCase(card(topicId = null))
        assertNull(repository.updateFlashcardCalls.single().topicId)
    }
}
