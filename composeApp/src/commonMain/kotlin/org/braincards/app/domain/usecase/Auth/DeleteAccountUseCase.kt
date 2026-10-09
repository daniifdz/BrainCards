package org.braincards.app.domain.usecase.Auth

import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.TopicsRepository

/**
 * Borra la cuenta del usuario (Bloque F). Orden deliberado:
 * 1. RPC remoto (authRepository.deleteAccount()) -- si lanza, se propaga sin tocar
 *    nada mas: ni las filas locales ni la sesion.
 * 2. Liberar las filas locales en los tres repositorios (userId/lastSyncedAt a null),
 *    solo alcanzable si el paso 1 tuvo exito.
 * 3. signOut() el ultimo paso, envuelto aparte: el borrado remoto y la liberacion
 *    local ya ocurrieron, asi que un fallo aqui (sesion que no se puede cerrar del
 *    todo) no debe aparentar que el borrado de cuenta fallo.
 */
class DeleteAccountUseCase(
    private val authRepository: AuthRepository,
    private val decksRepository: DecksRepository,
    private val topicsRepository: TopicsRepository,
    private val flashcardRepository: FlashcardRepository
) {
    suspend operator fun invoke(userId: String) {
        authRepository.deleteAccount()

        decksRepository.releaseFromUser(userId)
        topicsRepository.releaseFromUser(userId)
        flashcardRepository.releaseFromUser(userId)

        try {
            authRepository.signOut()
        } catch (e: Exception) {
            println("Cuenta borrada, pero signOut() local fallo: $e")
        }
    }
}
