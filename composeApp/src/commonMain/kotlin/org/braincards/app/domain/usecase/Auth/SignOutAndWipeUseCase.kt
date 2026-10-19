package org.braincards.app.domain.usecase.Auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.LocalDataRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tiempo maximo que se espera al logout remoto. Menos que el requestTimeout del cliente
 * (10 s): sin red, el usuario no debe esperar tanto para poder cerrar sesion.
 */
val REMOTE_SIGN_OUT_TIMEOUT: Duration = 4.seconds

sealed interface SignOutAndWipeResult {
    /** BD local vaciada y sesion cerrada. */
    data object SignedOut : SignOutAndWipeResult

    /** El usuario actual ya no es el esperado: no se borro nada ni se toco la sesion. */
    data object SessionChanged : SignOutAndWipeResult

    /** wipeAll() fallo (se revierte entero): no se cerro la sesion, se puede reintentar. */
    data class WipeFailed(val cause: Exception) : SignOutAndWipeResult

    /** La BD ya se vacio, pero ni signOut() ni clearLocalSession() cerraron la sesion. */
    data class SessionCloseFailed(val cause: Exception) : SignOutAndWipeResult
}

/**
 * Cierre de sesion con limpieza de la BD local (aislamiento de datos entre cuentas).
 *
 * Orden deliberado: primero se borra y despues se cierra la sesion, para que la sesion sea lo
 * ultimo que muere. Si algo falla o el proceso muere a medias, lo peor es "misma cuenta + BD
 * vacia" (se repuebla con el siguiente pull), nunca "otra identidad + datos de la anterior".
 *
 * 1. Revalida que la sesion actual sigue siendo [expectedUserId]; si no, no hace nada.
 * 2. wipeAll() (una transaccion). Si falla, devuelve WipeFailed SIN tocar la sesion.
 * 3. authRepository.signOut() con limite de [REMOTE_SIGN_OUT_TIMEOUT]. signOut() hace una
 *    peticion de red y, sin conexion, LANZA antes de limpiar la sesion local (auth-kt 3.1.4).
 *    Su firma devuelve Unit, asi que el unico modo de fallo es la excepcion (o agotar el
 *    tiempo); no existe un "fallo devuelto" que tratar aparte.
 * 4. Si signOut() lanza o se agota el tiempo, clearLocalSession() (sin red). Siempre se puede
 *    cerrar sesion, tambien sin conexion.
 *
 * El borrado y el cierre van en NonCancellable: si el usuario sale de la pantalla a mitad, no
 * puede quedar "BD vacia y sesion viva". El CALLER debe mantener el lock de sync durante toda
 * la llamada (hasta que la sesion local este limpia), o un sync en el hueco repoblaria la BD.
 */
class SignOutAndWipeUseCase(
    private val authRepository: AuthRepository,
    private val localDataRepository: LocalDataRepository
) {
    suspend operator fun invoke(expectedUserId: String): SignOutAndWipeResult {
        val current = authRepository.authState.first()
        if (current !is AuthState.Authenticated || current.userId != expectedUserId) {
            return SignOutAndWipeResult.SessionChanged
        }

        return withContext(NonCancellable) {
            try {
                localDataRepository.wipeAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext SignOutAndWipeResult.WipeFailed(e)
            }
            closeSession()
        }
    }

    private suspend fun closeSession(): SignOutAndWipeResult {
        val remoteSignOutSucceeded = try {
            withTimeoutOrNull(REMOTE_SIGN_OUT_TIMEOUT) {
                authRepository.signOut()
                true
            } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (remoteSignOutSucceeded) return SignOutAndWipeResult.SignedOut

        return try {
            authRepository.clearLocalSession()
            SignOutAndWipeResult.SignedOut
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SignOutAndWipeResult.SessionCloseFailed(e)
        }
    }
}
