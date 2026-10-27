package org.braincards.app.domain.usecase.Auth

import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.TopicsRepository
import org.braincards.app.util.logError

/**
 * Borra la cuenta del usuario (Bloque F). Orden deliberado:
 * 1. RPC remoto (authRepository.deleteAccount()) -- si lanza, se propaga sin tocar
 *    nada mas: ni las filas locales ni la sesion.
 * 2. Liberar las filas locales en los cuatro repositorios (userId/lastSyncedAt a null),
 *    solo alcanzable si el paso 1 tuvo exito. card_scheduling va PRIMERO (hijos antes
 *    que padres): si una liberacion fallara a medias, un scheduling en NULL siempre se
 *    puede volver a reclamar, mientras que un scheduling con el dueno viejo cuya
 *    flashcard ya fue liberada y reclamada por otra cuenta quedaria huerfano para
 *    siempre (ni pendiente ni reclamable). No hay transaccion unica entre repositorios.
 * 3. signOut() el ultimo paso, envuelto aparte: el borrado remoto y la liberacion
 *    local ya ocurrieron, asi que un fallo aqui (sesion que no se puede cerrar del
 *    todo) no debe aparentar que el borrado de cuenta fallo.
 */
class DeleteAccountUseCase(
    private val authRepository: AuthRepository,
    private val decksRepository: DecksRepository,
    private val topicsRepository: TopicsRepository,
    private val flashcardRepository: FlashcardRepository,
    private val cardSchedulingRepository: CardSchedulingRepository
) {
    suspend operator fun invoke(userId: String) {
        authRepository.deleteAccount()

        cardSchedulingRepository.releaseFromUser(userId)
        flashcardRepository.releaseFromUser(userId)
        topicsRepository.releaseFromUser(userId)
        decksRepository.releaseFromUser(userId)

        try {
            authRepository.signOut()
        } catch (e: Exception) {
            logError("Cuenta borrada, pero signOut() local fallo", e)
        }
    }
}
