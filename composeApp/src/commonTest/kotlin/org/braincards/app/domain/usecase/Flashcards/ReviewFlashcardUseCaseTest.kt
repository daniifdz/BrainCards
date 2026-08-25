package org.braincards.app.domain.usecase.Flashcards

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeReviewLogRepository
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// TODO(card_scheduling): roto a proposito, downstream de ReviewFlashcardUseCase (ver TODO
// alli). buildFlashcard() de aqui abajo construye Flashcard con easeFactor/interval/
// repetitions, que ya no existen en el modelo. Se retoma en la misma sesion.
class ReviewFlashcardUseCaseTest {

    private fun buildFlashcard(
        easeFactor: Double = 2.5,
        interval: Int = 0,
        repetitions: Int = 0
    ): Flashcard = Flashcard(
        id = "card-1",
        deckId = "deck-1",
        question = "Pregunta de prueba",
        answer = "Respuesta de prueba",
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        nextReview = Instant.fromEpochMilliseconds(0),
        interval = interval,
        repetitions = repetitions,
        cardType = CardType.FLIP,
        easeFactor = easeFactor
    )

    @Test
    fun easeFactor_no_baja_de_1_3_incluso_con_AGAIN_desde_un_valor_bajo() = runBlocking {
        val repository = FakeFlashcardRepository()
        val reviewLogRepository = FakeReviewLogRepository()
        val useCase = ReviewFlashcardUseCase(repository, reviewLogRepository)
        val flashcard = buildFlashcard(easeFactor = 1.5)

        useCase(flashcard, ReviewQuality.AGAIN)

        val updated = repository.updateFlashcardCalls.single()
        assertEquals(1.3, updated.easeFactor)
    }

    @Test
    fun un_fallo_resetea_repetitions_e_interval_a_0() = runBlocking {
        val repository = FakeFlashcardRepository()
        val reviewLogRepository = FakeReviewLogRepository()
        val useCase = ReviewFlashcardUseCase(repository, reviewLogRepository)
        val flashcard = buildFlashcard(interval = 20, repetitions = 5)

        useCase(flashcard, ReviewQuality.AGAIN)

        val updated = repository.updateFlashcardCalls.single()
        assertEquals(0, updated.repetitions)
        assertEquals(0, updated.interval)
    }

    @Test
    fun primer_acierto_pone_interval_en_1() = runBlocking {
        val repository = FakeFlashcardRepository()
        val reviewLogRepository = FakeReviewLogRepository()
        val useCase = ReviewFlashcardUseCase(repository, reviewLogRepository)
        val flashcard = buildFlashcard(repetitions = 0)

        useCase(flashcard, ReviewQuality.GOOD)

        val updated = repository.updateFlashcardCalls.single()
        assertEquals(1, updated.repetitions)
        assertEquals(1, updated.interval)
    }

    @Test
    fun segundo_acierto_pone_interval_en_6() = runBlocking {
        val repository = FakeFlashcardRepository()
        val reviewLogRepository = FakeReviewLogRepository()
        val useCase = ReviewFlashcardUseCase(repository, reviewLogRepository)
        val flashcard = buildFlashcard(repetitions = 1)

        useCase(flashcard, ReviewQuality.GOOD)

        val updated = repository.updateFlashcardCalls.single()
        assertEquals(2, updated.repetitions)
        assertEquals(6, updated.interval)
    }

    @Test
    fun acierto_posterior_usa_el_nuevo_easeFactor_no_el_original() = runBlocking {
        val repository = FakeFlashcardRepository()
        val reviewLogRepository = FakeReviewLogRepository()
        val useCase = ReviewFlashcardUseCase(repository, reviewLogRepository)
        // EASY con EF=2.5 -> nuevo EF=2.6 (distinto del original: 2.5 + 0.1).
        // Si el codigo usara el EF original (2.5) el interval saldria en 15
        // (round(6*2.5)); al usar el nuevo (2.6) sale en 16 (round(6*2.6)=15.6->16),
        // asi que este caso SI distingue si el orden de la formula esta invertido.
        val flashcard = buildFlashcard(easeFactor = 2.5, interval = 6, repetitions = 2)

        useCase(flashcard, ReviewQuality.EASY)

        val updated = repository.updateFlashcardCalls.single()
        assertEquals(2.6, updated.easeFactor, absoluteTolerance = 0.0001)
        assertEquals(16, updated.interval)
    }

    @Test
    fun se_registra_un_review_log_con_los_datos_correctos() = runBlocking {
        val repository = FakeFlashcardRepository()
        val reviewLogRepository = FakeReviewLogRepository()
        val useCase = ReviewFlashcardUseCase(repository, reviewLogRepository)
        val flashcard = buildFlashcard(easeFactor = 2.5, interval = 6, repetitions = 2)

        useCase(flashcard, ReviewQuality.GOOD)

        assertEquals(1, reviewLogRepository.addedLogs.size)
        val log = reviewLogRepository.addedLogs.single()
        assertEquals(flashcard.id, log.cardId)
        assertEquals(ReviewQuality.GOOD.value, log.grade)
        assertEquals(6, log.previousInterval)
        // GOOD desde EF=2.5 no cambia el EF -> round(6*2.5) = 15
        assertEquals(15, log.newInterval)
    }

    @Test
    fun se_actualiza_la_flashcard_con_los_valores_nuevos() = runBlocking {
        val repository = FakeFlashcardRepository()
        val reviewLogRepository = FakeReviewLogRepository()
        val useCase = ReviewFlashcardUseCase(repository, reviewLogRepository)
        val flashcard = buildFlashcard(easeFactor = 2.5, interval = 0, repetitions = 0)

        useCase(flashcard, ReviewQuality.GOOD)

        assertEquals(1, repository.updateFlashcardCalls.size)
        val updated = repository.updateFlashcardCalls.single()
        assertEquals(2.5, updated.easeFactor)
        assertEquals(1, updated.repetitions)
        assertEquals(1, updated.interval)
        assertNotEquals(flashcard.nextReview, updated.nextReview)
    }
}
