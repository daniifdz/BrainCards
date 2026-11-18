package org.braincards.app.domain.usecase.Flashcards

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeReviewLogRepository
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Los dos recorders: la sesion normal guarda; el repaso libre NO toca nada. */
class RatingRecorderTest {

    private val t0 = Instant.fromEpochMilliseconds(1_000)
    private val card = Flashcard(
        id = "c1", deckId = "d1", question = "Q", answer = "A", createdAt = t0, updatedAt = t0, cardType = CardType.FLIP
    )

    @Test
    fun el_recorder_programado_guarda_la_programacion_y_el_historial() = runTest {
        val log = FakeReviewLogRepository()
        val scheduling = FakeCardSchedulingRepository()
        ScheduledRecorder(ReviewFlashcardUseCase(log, scheduling)).record(card, ReviewQuality.GOOD)
        assertEquals(1, log.addedLogs.size)
        assertEquals(1, scheduling.upsertCalls.size)
        assertEquals("c1", scheduling.upsertCalls.single().cardId)
    }

    @Test
    fun el_recorder_libre_no_hace_nada_con_ninguna_calidad() = runTest {
        // FreeRecorder no tiene dependencias: no puede escribir. Se comprueba ademas que usarlo no altera repositorios ajenos.
        val log = FakeReviewLogRepository()
        val scheduling = FakeCardSchedulingRepository()
        for (q in ReviewQuality.entries) FreeRecorder.record(card, q)
        assertTrue(log.addedLogs.isEmpty())
        assertTrue(scheduling.upsertCalls.isEmpty())
    }
}
