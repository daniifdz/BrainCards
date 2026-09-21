package org.braincards.app.domain.usecase.Sync

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.RemoteDataSource
import org.braincards.app.domain.repository.TopicsRepository
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase

sealed interface SyncResult {
    data object NotAuthenticated : SyncResult
    data class Completed(val pull: PullResult, val push: PushResult) : SyncResult
}

data class PullResult(
    val decks: PullTableResult,
    val topics: PullTableResult,
    val flashcards: PullTableResult
)

data class PushResult(
    val decks: PushTableResult,
    val topics: PushTableResult,
    val flashcards: PushTableResult
)

/**
 * [applied] son las filas remotas que se aplicaron localmente. Si [aborted] es true,
 * [applied] siempre es 0 -- la tabla es todo o nada (ver comentario en pullTable).
 */
data class PullTableResult(val applied: Int, val aborted: Boolean)

/**
 * [failed] incluye tanto las filas que se intentaron y fallaron como las que ni
 * siquiera se llegaron a intentar por el corte de fallos consecutivos de red --
 * ambas quedan igual de pendientes para el siguiente sync().
 */
data class PushTableResult(val succeeded: Int, val failed: Int)

/**
 * Orquesta RemoteDataSource + los metodos aditivos de sync (getPendingSync /
 * applyRemotePull / markPushed) de cada repositorio. No toca el CRUD local de los
 * RepositoryImpl ni las interfaces de dominio -- ver regla de oro en CLAUDE.md.
 *
 * pull() va siempre antes que push(): si se hiciera al reves, un pull posterior
 * (REPLACE) podria pisar con una version remota mas vieja lo que acabamos de subir.
 *
 * RIESGO ABIERTO A PROPOSITO (ver FINAL_SPRINT.md, Sprint 12 Bloque D): el pull
 * aplica con REPLACE sin comparar -- una edicion local pendiente de subir se pierde
 * en silencio si llega un pull de esa misma fila desde otro dispositivo antes del
 * siguiente push. Aceptado con cero usuarios reales; lo cierra la resolucion de
 * conflictos (Bloque E).
 */
class SyncEngineUseCase(
    private val authRepository: AuthRepository,
    private val remoteDataSource: RemoteDataSource,
    private val decksRepository: DecksRepository,
    private val topicsRepository: TopicsRepository,
    private val flashcardRepository: FlashcardRepository,
    private val backfillLocalDataUseCase: BackfillLocalDataUseCase
) {

    suspend operator fun invoke(): SyncResult {
        val userId = currentUserId() ?: return SyncResult.NotAuthenticated
        // AuthViewModel ya llama a esto una vez en el login, pero cualquier
        // deck/topic/flashcard creado despues (con sesion ya activa) nace con
        // userId = null -- nada mas lo asigna al crear. Sin este repaso aqui, esas
        // filas se quedan huerfanas para siempre: getPendingSync(userId) nunca las
        // ve porque su userId sigue siendo null. Idempotente (WHERE userId IS NULL),
        // asi que no hace dano llamarlo en cada sync.
        backfillLocalDataUseCase(userId)
        val pullResult = pull(userId)
        // Se sigue con push aunque el pull haya fallado (parcial o totalmente):
        // push no depende de que el pull haya tenido exito, y asi el usuario al
        // menos sube lo que tiene aunque no se haya podido comprobar lo remoto.
        val pushResult = push(userId)
        return SyncResult.Completed(pullResult, pushResult)
    }

    private suspend fun currentUserId(): String? =
        when (val state = authRepository.authState.first()) {
            is AuthState.Authenticated -> state.userId
            else -> null
        }

    // decks -> topics -> flashcards, secuencial y bloqueante: una flashcard no se
    // puede insertar localmente si su deck (FK real en Room) todavia no existe.
    private suspend fun pull(userId: String): PullResult {
        val decksResult = pullTable(
            fetch = { remoteDataSource.pullDecks(userId, PULL_SINCE_EPOCH) },
            apply = { decksRepository.applyRemotePull(it) }
        )
        val topicsResult = pullTable(
            fetch = { remoteDataSource.pullTopics(userId, PULL_SINCE_EPOCH) },
            apply = { topicsRepository.applyRemotePull(it) }
        )
        val flashcardsResult = pullTable(
            fetch = { remoteDataSource.pullFlashcards(userId, PULL_SINCE_EPOCH) },
            apply = { flashcardRepository.applyRemotePull(it) }
        )
        return PullResult(decksResult, topicsResult, flashcardsResult)
    }

    private suspend fun push(userId: String): PushResult {
        val now = Clock.System.now()
        val decksResult = pushTable(
            pending = decksRepository.getPendingSync(userId),
            rowId = { it.id },
            pushOne = { remoteDataSource.pushDecks(listOf(it)) },
            markPushed = { ids -> decksRepository.markPushed(ids, now) }
        )
        val topicsResult = pushTable(
            pending = topicsRepository.getPendingSync(userId),
            rowId = { it.id },
            pushOne = { remoteDataSource.pushTopics(listOf(it)) },
            markPushed = { ids -> topicsRepository.markPushed(ids, now) }
        )
        val flashcardsResult = pushTable(
            pending = flashcardRepository.getPendingSync(userId),
            rowId = { it.id },
            pushOne = { remoteDataSource.pushFlashcards(listOf(it)) },
            markPushed = { ids -> flashcardRepository.markPushed(ids, now) }
        )
        return PushResult(decksResult, topicsResult, flashcardsResult)
    }

    /**
     * Todo o nada por tabla: [apply] inserta la tabla entera dentro de una unica
     * llamada a un DAO @Insert con lista, que Room ya envuelve en su propia
     * transaccion (confirmado en el codigo generado por KSP, DBUtil.performSuspending
     * con inTransaction = true) -- si falla a mitad, esa tabla se aborta entera y no
     * se sigue con la siguiente tabla de pull.
     */
    private suspend fun <T> pullTable(
        fetch: suspend () -> List<T>,
        apply: suspend (List<T>) -> Unit
    ): PullTableResult {
        return try {
            val remoteRows = fetch()
            apply(remoteRows)
            PullTableResult(applied = remoteRows.size, aborted = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PullTableResult(applied = 0, aborted = true)
        }
    }

    /**
     * Resiliente fila a fila: una fila que falla no bloquea las siguientes y
     * simplemente no se marca pushed (se reintenta sola en el siguiente sync()).
     * Corta el bucle tras CONSECUTIVE_NETWORK_FAILURE_LIMIT fallos CONSECUTIVOS de
     * tipo red/timeout/5xx -- un fallo de validacion (4xx) resetea ese contador sin
     * cortar, porque no indica que estemos sin conexion.
     */
    private suspend fun <T> pushTable(
        pending: List<T>,
        rowId: (T) -> String,
        pushOne: suspend (T) -> Unit,
        markPushed: suspend (List<String>) -> Unit
    ): PushTableResult {
        var consecutiveNetworkFailures = 0
        val pushedIds = mutableListOf<String>()
        for (row in pending) {
            if (consecutiveNetworkFailures >= CONSECUTIVE_NETWORK_FAILURE_LIMIT) break
            try {
                pushOne(row)
                pushedIds.add(rowId(row))
                consecutiveNetworkFailures = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // TODO DEBUG TEMPORAL -- quitar en cuanto se resuelva el problema de push
                // que se esta investigando ahora mismo. No cambia la logica de reintentos.
                val detail = if (e is PostgrestRestException) {
                    "PostgrestRestException statusCode=${e.statusCode} code=${e.code} details=${e.details} hint=${e.hint} message=${e.message}"
                } else {
                    "${e::class.simpleName}: ${e.message}"
                }
                println("SyncEngineUseCase.pushTable FALLO fila id=${rowId(row)}: $detail")
                e.printStackTrace()
                consecutiveNetworkFailures =
                    if (e.isNetworkOrServerFailure()) consecutiveNetworkFailures + 1 else 0
            }
        }
        if (pushedIds.isNotEmpty()) markPushed(pushedIds)
        return PushTableResult(succeeded = pushedIds.size, failed = pending.size - pushedIds.size)
    }

    /**
     * Clasificacion verificada contra el codigo fuente real de supabase-kt 3.1.4 y
     * ktor-client-core 3.1.2 (no asumida):
     * - HttpRequestTimeoutException / HttpRequestException: no hubo respuesta HTTP
     *   (timeout o fallo de transporte -- DNS, conexion rechazada, TLS...).
     * - PostgrestRestException con statusCode >= 500: hubo respuesta, pero el fallo
     *   es del lado de Supabase, no un dato invalido que enviamos nosotros -- mismo
     *   trato que un timeout.
     * - PostgrestRestException con statusCode < 500 (400/401/404/409...): rechazo
     *   real del cliente -- no cuenta como red.
     */
    private fun Exception.isNetworkOrServerFailure(): Boolean = when (this) {
        is HttpRequestTimeoutException -> true
        is HttpRequestException -> true
        is PostgrestRestException -> statusCode >= 500
        else -> false
    }

    private companion object {
        const val CONSECUTIVE_NETWORK_FAILURE_LIMIT = 3

        // "Desde siempre": evita Instant.DISTANT_PAST, cuyo año está fuera del rango
        // que Postgres acepta para timestamptz. TODO (no bloqueante, no v1.1): sin un
        // watermark persistido por tabla, cada sync() vuelve a pedir la tabla remota
        // entera -- correcto pero no escala; revisar si hace falta antes de tener
        // datasets grandes de verdad.
        val PULL_SINCE_EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
