package org.braincards.app.domain.usecase.Auth

import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.TopicsRepository

/**
 * Adopta como propios (userId = null -> uid real) los datos locales creados antes de
 * tener cuenta. Idempotente por diseno: el WHERE userId IS NULL de cada DAO hace que,
 * si ya se adoptaron en un login anterior, la siguiente llamada no encuentre ninguna
 * fila que tocar -- no hace falta detectar "es la primera vez" por separado.
 *
 * Solo local, no toca Supabase -- eso es el Bloque D (RemoteDataSource/motor de sync).
 */
class BackfillLocalDataUseCase(
    private val decksRepository: DecksRepository,
    private val topicsRepository: TopicsRepository,
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(userId: String) {
        decksRepository.claimOrphanedDecks(userId)
        topicsRepository.claimOrphanedTopics(userId)
        flashcardRepository.claimOrphanedFlashcards(userId)
    }
}
