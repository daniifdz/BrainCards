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
 * Borra la cuenta del usuario. Orden deliberado:
 * 1. RPC remoto (authRepository.deleteAccount()). Si lanza, se propaga sin tocar nada más: ni las filas locales ni la sesión.
 * 2. Libera las filas locales en los cuatro repositorios (userId y lastSyncedAt a null); solo se llega aquí si el paso 1
 *    tuvo éxito. card_scheduling va primero (hijos antes que padres): si una liberación fallara a medias, un scheduling en
 *    NULL siempre se puede volver a reclamar, mientras que uno con el dueño viejo cuya tarjeta ya fue liberada y reclamada
 *    por otra cuenta quedaría huérfano para siempre. No hay transacción única entre repositorios.
 * 3. signOut() el último, envuelto aparte: el borrado remoto y la liberación local ya ocurrieron, así que un fallo aquí no
 *    debe aparentar que el borrado falló. Si signOut() falla (p. ej. sin red) se intenta clearLocalSession(), que limpia
 *    solo la sesión local, sin red y sin tocar los datos locales (nunca wipeAll: el usuario conserva sus mazos). Solo si
 *    también falla se devuelve [DeleteAccountResult.DeletedSessionNotClosed].
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
