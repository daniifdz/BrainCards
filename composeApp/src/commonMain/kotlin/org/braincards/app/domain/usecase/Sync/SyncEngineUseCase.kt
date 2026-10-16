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
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.RemoteDataSource
import org.braincards.app.domain.repository.TopicsRepository
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase

sealed interface SyncResult {
    data object NotAuthenticated : SyncResult
    data class Completed(val pull: PullResult, val push: PushResult) : SyncResult
}

// Bloque D (sin conexion y reintentos) -- usado por SyncCoordinator para decidir
// si persistir "ultima sincronizacion correcta". No reemplaza la logica mas fina
// de AuthViewModel.toUserMessage() (esa necesita los conteos por separado para
// distinguir pull parcial de push parcial en el mensaje al usuario).
fun SyncResult.Completed.isFullySuccessful(): Boolean =
    pull.abortedTableCount() == 0 && push.failedRowCount() == 0

data class PullResult(
    val decks: PullTableResult,
    val topics: PullTableResult,
    val flashcards: PullTableResult,
    val cardScheduling: PullTableResult
)

data class PushResult(
    val decks: PushTableResult,
    val topics: PushTableResult,
    val flashcards: PushTableResult,
    val cardScheduling: PushTableResult
)

/** Tablas (de las cuatro) cuyo pull se aborto. Unico sitio donde se cuentan: lo usan
 * isFullySuccessful() y AuthViewModel.toUserMessage(). */
fun PullResult.abortedTableCount(): Int =
    listOf(decks, topics, flashcards, cardScheduling).count { it.aborted }

/** Filas (de las cuatro tablas) que quedaron pendientes de subir. */
fun PushResult.failedRowCount(): Int =
    decks.failed + topics.failed + flashcards.failed + cardScheduling.failed

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
    private val cardSchedulingRepository: CardSchedulingRepository,
    private val backfillLocalDataUseCase: BackfillLocalDataUseCase
) {

    suspend operator fun invoke(): SyncResult {
        val userId = currentUserId() ?: return SyncResult.NotAuthenticated
        // Unico sitio que llama al backfill (AuthViewModel ya no lo hace): cualquier
        // deck/topic/flashcard/scheduling creado con sesion ya activa nace con
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

    // decks -> topics -> flashcards -> scheduling: una flashcard no se puede insertar
    // localmente si su deck (FK real en Room) todavia no existe, ni un scheduling sin su
    // flashcard (el repositorio descarta esas filas sin error). Cada tabla es
    // independiente: si una se aborta, las siguientes se intentan igualmente.
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
        val cardSchedulingResult = pullTable(
            fetch = { remoteDataSource.pullCardScheduling(userId, PULL_SINCE_EPOCH) },
            apply = { cardSchedulingRepository.applyRemotePull(it) }
        )
        return PullResult(decksResult, topicsResult, flashcardsResult, cardSchedulingResult)
    }

    private suspend fun push(userId: String): PushResult {
        // `now` se captura ANTES de cualquier getPendingSync: markPushed fija
        // lastSyncedAt = now, y una fila editada/repasada despues tiene updatedAt > now,
        // asi que sigue pendiente (updatedAt > lastSyncedAt) en vez de darse por subida.
        val now = Clock.System.now()
        val decksResult = pushTable(
            pending = decksRepository.getPendingSync(userId),
            rowId = { it.id },
            pushBatch = { remoteDataSource.pushDecks(it) },
            markPushed = { ids -> decksRepository.markPushed(ids, now) }
        )
        val topicsResult = pushTable(
            pending = topicsRepository.getPendingSync(userId),
            rowId = { it.id },
            pushBatch = { remoteDataSource.pushTopics(it) },
            markPushed = { ids -> topicsRepository.markPushed(ids, now) }
        )
        val flashcardsResult = pushTable(
            pending = flashcardRepository.getPendingSync(userId),
            rowId = { it.id },
            pushBatch = { remoteDataSource.pushFlashcards(it) },
            markPushed = { ids -> flashcardRepository.markPushed(ids, now) }
        )
        // Scheduling es la tabla de mayor rotacion (una fila por tarjeta repasada): se sube
        // en lotes de 100. Un fallo de red corta la tabla a la primera (limite 1): con 100
        // filas por peticion no tiene sentido esperar a 3 lotes fallidos sin conexion.
        val cardSchedulingResult = pushTable(
            pending = cardSchedulingRepository.getPendingSync(userId),
            rowId = { it.cardId },
            pushBatch = { remoteDataSource.pushCardScheduling(it) },
            markPushed = { ids -> cardSchedulingRepository.markPushed(ids, now) },
            batchSize = CARD_SCHEDULING_PUSH_BATCH_SIZE,
            networkFailureLimit = 1
        )
        return PushResult(decksResult, topicsResult, flashcardsResult, cardSchedulingResult)
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
        apply: suspend (List<T>) -> Int
    ): PullTableResult {
        return try {
            val remoteRows = fetch()
            val appliedCount = apply(remoteRows)
            PullTableResult(applied = appliedCount, aborted = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PullTableResult(applied = 0, aborted = true)
        }
    }

    /**
     * Resiliente por fila (o por lote): una fila que falla no bloquea las siguientes y
     * simplemente no se marca pushed (se reintenta sola en el siguiente sync()).
     *
     * - [batchSize] = 1 (decks/topics/flashcards): una peticion por fila. Corta el bucle
     *   tras [networkFailureLimit] (3) fallos CONSECUTIVOS de tipo red/timeout/5xx -- un
     *   fallo de validacion (4xx) resetea ese contador sin cortar, porque no indica que
     *   estemos sin conexion.
     * - [batchSize] > 1 (card_scheduling, 100): una peticion por lote (upsert atomico).
     *   Si el lote falla por error NO de red (4xx, o una fila invalida), se reintenta
     *   fila a fila SOLO ese lote para que las filas buenas no se pierdan por una mala.
     *   Si falla por red/timeout/5xx NO hay reintento fila a fila: con limite 1 se aborta
     *   la tabla; lo no subido queda pendiente (cuenta como failed).
     *
     * [markPushed] se llama una sola vez, al final, SOLO con los ids que se subieron.
     */
    private suspend fun <T> pushTable(
        pending: List<T>,
        rowId: (T) -> String,
        pushBatch: suspend (List<T>) -> Unit,
        markPushed: suspend (List<String>) -> Unit,
        batchSize: Int = 1,
        networkFailureLimit: Int = CONSECUTIVE_NETWORK_FAILURE_LIMIT
    ): PushTableResult {
        var consecutiveNetworkFailures = 0
        val pushedIds = mutableListOf<String>()

        suspend fun attempt(rows: List<T>): PushAttempt {
            try {
                pushBatch(rows)
                pushedIds.addAll(rows.map(rowId))
                consecutiveNetworkFailures = 0
                return PushAttempt.UPLOADED
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
                val rowsLabel = if (rows.size == 1) rowId(rows.single()) else "lote de ${rows.size} filas"
                println("SyncEngineUseCase.pushTable FALLO fila id=$rowsLabel: $detail")
                e.printStackTrace()
                return if (e.isNetworkOrServerFailure()) {
                    consecutiveNetworkFailures++
                    PushAttempt.NETWORK_FAILURE
                } else {
                    consecutiveNetworkFailures = 0
                    PushAttempt.REJECTED
                }
            }
        }

        for (batch in pending.chunked(batchSize)) {
            if (consecutiveNetworkFailures >= networkFailureLimit) break
            val outcome = attempt(batch)
            if (outcome == PushAttempt.REJECTED && batch.size > 1) {
                for (row in batch) {
                    if (consecutiveNetworkFailures >= networkFailureLimit) break
                    attempt(listOf(row))
                }
            }
        }
        if (pushedIds.isNotEmpty()) markPushed(pushedIds)
        return PushTableResult(succeeded = pushedIds.size, failed = pending.size - pushedIds.size)
    }

    private enum class PushAttempt { UPLOADED, REJECTED, NETWORK_FAILURE }

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
        const val CARD_SCHEDULING_PUSH_BATCH_SIZE = 100

        // "Desde siempre": evita Instant.DISTANT_PAST, cuyo año está fuera del rango
        // que Postgres acepta para timestamptz. TODO (no bloqueante, no v1.1): sin un
        // watermark persistido por tabla, cada sync() vuelve a pedir la tabla remota
        // entera -- correcto pero no escala; revisar si hace falta antes de tener
        // datasets grandes de verdad.
        val PULL_SINCE_EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
