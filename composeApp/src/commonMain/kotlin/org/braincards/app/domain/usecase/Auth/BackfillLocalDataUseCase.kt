package org.braincards.app.domain.usecase.Auth

import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.TopicsRepository

/**
 * Adopta como propios (userId = null -> uid real) los datos locales creados antes de
 * tener cuenta. Idempotente por diseno: el WHERE userId IS NULL de cada DAO hace que,
 * si ya se adoptaron en un login anterior, la siguiente llamada no encuentre ninguna
 * fila que tocar -- no hace falta detectar "es la primera vez" por separado.
 *
 * ORDEN: card_scheduling va el ULTIMO. Su reclamo solo adopta filas cuya flashcard ya
 * pertenece a [userId] (el scheduling debe tener el mismo dueno que su flashcard, lo
 * exige la RLS de Supabase), asi que las flashcards tienen que reclamarse antes.
 *
 * Solo local, no toca Supabase -- eso es el Bloque D (RemoteDataSource/motor de sync).
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
