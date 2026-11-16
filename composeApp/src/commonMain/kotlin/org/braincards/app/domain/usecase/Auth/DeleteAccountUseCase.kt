package org.braincards.app.domain.usecase.Auth

import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.TopicsRepository
import kotlinx.coroutines.CancellationException
import org.braincards.app.util.logError

/** Como acabo un borrado de cuenta que SI llego a borrar la cuenta remota (si el RPC falla, la funcion lanza). */
enum class DeleteAccountResult {
    /** Cuenta borrada, filas locales liberadas y sesion local cerrada. */
    Deleted,

    /** Cuenta borrada y filas liberadas, pero ni signOut() ni clearLocalSession() cerraron la sesion local. */
    DeletedSessionNotClosed
}

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
 *    todo) no debe aparentar que el borrado de cuenta fallo. Si signOut() falla (p. ej.
 *    sin red) se intenta clearLocalSession(): limpia SOLO la sesion local, sin red y SIN
 *    tocar los datos locales (nunca wipeAll: el usuario conserva sus mazos). Solo si
 *    tambien falla se devuelve [DeleteAccountResult.DeletedSessionNotClosed].
 */
class DeleteAccountUseCase(
    private val authRepository: AuthRepository,
    private val decksRepository: DecksRepository,
    private val topicsRepository: TopicsRepository,
    private val flashcardRepository: FlashcardRepository,
    private val cardSchedulingRepository: CardSchedulingRepository
) {
    suspend operator fun invoke(userId: String): DeleteAccountResult {
        authRepository.deleteAccount()

        cardSchedulingRepository.releaseFromUser(userId)
        flashcardRepository.releaseFromUser(userId)
        topicsRepository.releaseFromUser(userId)
        decksRepository.releaseFromUser(userId)

        return try {
            authRepository.signOut()
            DeleteAccountResult.Deleted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("Cuenta borrada, pero signOut() fallo", e)
            clearLocalSessionOnly()
        }
    }

    private suspend fun clearLocalSessionOnly(): DeleteAccountResult = try {
        authRepository.clearLocalSession()
        DeleteAccountResult.Deleted
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logError("Cuenta borrada, pero clearLocalSession() fallo", e)
        DeleteAccountResult.DeletedSessionNotClosed
    }
}
