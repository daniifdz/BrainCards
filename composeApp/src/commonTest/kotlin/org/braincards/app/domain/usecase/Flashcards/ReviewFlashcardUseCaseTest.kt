package org.braincards.app.domain.usecase.Flashcards

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeReviewLogRepository
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class ReviewFlashcardUseCaseTest {

    private fun buildFlashcard(): Flashcard = Flashcard(
        id = "card-1",
        deckId = "deck-1",
        question = "Pregunta de prueba",
        answer = "Respuesta de prueba",
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        cardType = CardType.FLIP
    )

    // Semilla del CardScheduling ya existente para la tarjeta (el punto de partida que
    // antes vivia en la propia Flashcard). No sembrar nada simula una tarjeta que
    // todavia no se ha repasado nunca.
    private fun buildScheduling(
        easeFactor: Double = 2.5,
        interval: Int = 0,
        repetitions: Int = 0,
        nextReview: Instant = Instant.fromEpochMilliseconds(0)
    ): CardScheduling = CardScheduling(
        cardId = "card-1",
        easeFactor = easeFactor,
        interval = interval,
        repetitions = repetitions,
        nextReview = nextReview,
        updatedAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun easeFactor_no_baja_de_1_3_incluso_con_AGAIN_desde_un_valor_bajo() = runBlocking {
        val reviewLogRepository = FakeReviewLogRepository()
        val cardSchedulingRepository = FakeCardSchedulingRepository()
        cardSchedulingRepository.upsert(buildScheduling(easeFactor = 1.5))
        val useCase = ReviewFlashcardUseCase(reviewLogRepository, cardSchedulingRepository)
        val flashcard = buildFlashcard()

        useCase(flashcard, ReviewQuality.AGAIN)

        val updated = cardSchedulingRepository.upsertCalls.last()
        assertEquals(1.3, updated.easeFactor)
    }

    @Test
    fun un_fallo_resetea_repetitions_e_interval_a_0() = runBlocking {
        val reviewLogRepository = FakeReviewLogRepository()
        val cardSchedulingRepository = FakeCardSchedulingRepository()
        cardSchedulingRepository.upsert(buildScheduling(interval = 20, repetitions = 5))
        val useCase = ReviewFlashcardUseCase(reviewLogRepository, cardSchedulingRepository)
        val flashcard = buildFlashcard()

        useCase(flashcard, ReviewQuality.AGAIN)

        val updated = cardSchedulingRepository.upsertCalls.last()
        assertEquals(0, updated.repetitions)
        assertEquals(0, updated.interval)
    }

    @Test
    fun primer_acierto_pone_interval_en_1() = runBlocking {
        val reviewLogRepository = FakeReviewLogRepository()
        val cardSchedulingRepository = FakeCardSchedulingRepository()
        // sin seed: tarjeta nunca repasada, usa los valores por defecto (repetitions=0)
        val useCase = ReviewFlashcardUseCase(reviewLogRepository, cardSchedulingRepository)
        val flashcard = buildFlashcard()

        useCase(flashcard, ReviewQuality.GOOD)

        val updated = cardSchedulingRepository.upsertCalls.single()
        assertEquals(1, updated.repetitions)
        assertEquals(1, updated.interval)
    }

    @Test
    fun segundo_acierto_pone_interval_en_6() = runBlocking {
        val reviewLogRepository = FakeReviewLogRepository()
        val cardSchedulingRepository = FakeCardSchedulingRepository()
        cardSchedulingRepository.upsert(buildScheduling(repetitions = 1))
        val useCase = ReviewFlashcardUseCase(reviewLogRepository, cardSchedulingRepository)
        val flashcard = buildFlashcard()

        useCase(flashcard, ReviewQuality.GOOD)

        val updated = cardSchedulingRepository.upsertCalls.last()
        assertEquals(2, updated.repetitions)
        assertEquals(6, updated.interval)
    }

    @Test
    fun acierto_posterior_usa_el_nuevo_easeFactor_no_el_original() = runBlocking {
        val reviewLogRepository = FakeReviewLogRepository()
        val cardSchedulingRepository = FakeCardSchedulingRepository()
        // EASY con EF=2.5 -> nuevo EF=2.6 (distinto del original: 2.5 + 0.1).
        // Si el codigo usara el EF original (2.5) el interval saldria en 15
        // (round(6*2.5)); al usar el nuevo (2.6) sale en 16 (round(6*2.6)=15.6->16),
        // asi que este caso SI distingue si el orden de la formula esta invertido.
        cardSchedulingRepository.upsert(buildScheduling(easeFactor = 2.5, interval = 6, repetitions = 2))
        val useCase = ReviewFlashcardUseCase(reviewLogRepository, cardSchedulingRepository)
        val flashcard = buildFlashcard()

        useCase(flashcard, ReviewQuality.EASY)

        val updated = cardSchedulingRepository.upsertCalls.last()
        assertEquals(2.6, updated.easeFactor, absoluteTolerance = 0.0001)
        assertEquals(16, updated.interval)
    }

    @Test
    fun se_registra_un_review_log_con_los_datos_correctos() = runBlocking {
        val reviewLogRepository = FakeReviewLogRepository()
        val cardSchedulingRepository = FakeCardSchedulingRepository()
        cardSchedulingRepository.upsert(buildScheduling(easeFactor = 2.5, interval = 6, repetitions = 2))
        val useCase = ReviewFlashcardUseCase(reviewLogRepository, cardSchedulingRepository)
        val flashcard = buildFlashcard()

        useCase(flashcard, ReviewQuality.GOOD)

        assertEquals(1, reviewLogRepository.addedLogs.size)
        val log = reviewLogRepository.addedLogs.single()
        assertEquals(flashcard.id, log.cardId)
        assertEquals(ReviewQuality.GOOD.value, log.grade)
        assertEquals(6, log.previousInterval)
        // GOOD desde EF=2.5 no cambia el EF -> round(6*2.5) = 15
        assertEquals(15, log.newInterval)
    }

    // Tarjeta nueva: no tiene fila en card_scheduling hasta su primer repaso. El caso de uso
    // debe partir de los valores SM-2 por defecto (EF 2.5, interval 0, repetitions 0) y crearla.
    @Test
    fun una_tarjeta_sin_fila_previa_parte_de_los_valores_por_defecto_y_se_crea_su_fila() = runBlocking {
        val reviewLogRepository = FakeReviewLogRepository()
        val cardSchedulingRepository = FakeCardSchedulingRepository()
        val useCase = ReviewFlashcardUseCase(reviewLogRepository, cardSchedulingRepository)
        val flashcard = buildFlashcard()

        assertEquals(null, cardSchedulingRepository.getByCardId(flashcard.id))

        useCase(flashcard, ReviewQuality.GOOD)

        val created = cardSchedulingRepository.getByCardId(flashcard.id)
        assertNotNull(created)
        assertEquals(1, cardSchedulingRepository.upsertCalls.size)
        assertEquals(2.5, created.easeFactor)
        assertEquals(1, created.repetitions)
        assertEquals(1, created.interval)
        assertEquals(0, reviewLogRepository.addedLogs.single().previousInterval)
    }

    // Renombrado respecto al original ("se_actualiza_la_flashcard..."): desde que
    // ReviewFlashcardUseCase dejo de tocar Flashcard, lo que se actualiza y persiste
    // es el CardScheduling, no la flashcard.
    @Test
    fun se_guarda_el_card_scheduling_actualizado_con_los_valores_nuevos() = runBlocking {
        val reviewLogRepository = FakeReviewLogRepository()
        val cardSchedulingRepository = FakeCardSchedulingRepository()
        val existing = buildScheduling(easeFactor = 2.5, interval = 0, repetitions = 0)
        cardSchedulingRepository.upsert(existing)
        val useCase = ReviewFlashcardUseCase(reviewLogRepository, cardSchedulingRepository)
        val flashcard = buildFlashcard()

        useCase(flashcard, ReviewQuality.GOOD)

        // 2 upserts: el seed inicial de este test + el que hace el propio caso de uso
        assertEquals(2, cardSchedulingRepository.upsertCalls.size)
        val updated = cardSchedulingRepository.upsertCalls.last()
        assertEquals(2.5, updated.easeFactor)
        assertEquals(1, updated.repetitions)
        assertEquals(1, updated.interval)
        assertNotEquals(existing.nextReview, updated.nextReview)
    }
}
