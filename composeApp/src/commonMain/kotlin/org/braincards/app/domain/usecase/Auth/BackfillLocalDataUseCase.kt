package org.braincards.app.domain.usecase.Auth

import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.TopicsRepository

/**
 * Adopta como propios (userId = null -> uid real) los datos locales creados antes de tener cuenta. Es idempotente: el
 * WHERE userId IS NULL de cada DAO hace que, si ya se adoptaron en un login anterior, la siguiente llamada no encuentre
 * filas que tocar.
 *
 * Orden: card_scheduling va el último. Su reclamo solo adopta filas cuya tarjeta ya pertenece a [userId] (el scheduling
 * debe tener el mismo dueño que su tarjeta; lo exige la RLS de Supabase), así que las tarjetas se reclaman antes.
 *
 * Solo local: no toca Supabase.
 */
class BackfillLocalDataUseCase(
    private val decksRepository: DecksRepository,
    private val topicsRepository: TopicsRepository,
    private val flashcardRepository: FlashcardRepository,
    private val cardSchedulingRepository: CardSchedulingRepository
) {
    suspend operator fun invoke(userId: String) {
        decksRepository.claimOrphanedDecks(userId)
        topicsRepository.claimOrphanedTopics(userId)
        flashcardRepository.claimOrphanedFlashcards(userId)
        cardSchedulingRepository.claimOrphanedScheduling(userId)
    }
}
