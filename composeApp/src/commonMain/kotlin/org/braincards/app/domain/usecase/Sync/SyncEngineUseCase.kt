package org.braincards.app.domain.usecase.Sync

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.MissingServerUpdatedAtException
import org.braincards.app.domain.models.PullApplyReport
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.PullWatermarks
import org.braincards.app.domain.models.RemoteRow
import org.braincards.app.domain.models.SyncTable
import org.braincards.app.domain.repository.AuthRepository
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.LocalTableInspector
import org.braincards.app.domain.repository.NeverEmptyLocalTableInspector
import org.braincards.app.domain.repository.NoOpPullWatermarkStore
import org.braincards.app.domain.repository.PullWatermarkStore
import org.braincards.app.domain.repository.RemoteDataSource
import org.braincards.app.domain.repository.TopicsRepository
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** Cada cuanto se fuerza un pull COMPLETO de las cuatro tablas aunque haya cursores guardados. */
val FULL_PULL_INTERVAL: Duration = 7.days

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

/** Filas (de las cuatro tablas) descartadas por la guarda de padres del pull. */
fun PullResult.discardedRowCount(): Int =
    listOf(decks, topics, flashcards, cardScheduling).sumOf { it.discarded }

/** Filas (de las cuatro tablas) descargadas del servidor y aplicadas sin abortar la tabla. */
fun PullResult.downloadedRowCount(): Int =
    listOf(decks, topics, flashcards, cardScheduling).sumOf { it.downloaded }

/** Filas (de las cuatro tablas) que quedaron pendientes de subir. */
fun PushResult.failedRowCount(): Int =
    decks.failed + topics.failed + flashcards.failed + cardScheduling.failed

/** Por que se aborto el pull de una tabla (lo rellena la clasificacion de pullTable). */
enum class PullFailure {
    /** Sin conexion, timeout o error 5xx del servidor. */
    NETWORK,

    /** El servidor rechazo la peticion (4xx: sesion o permisos). */
    REJECTED,

    /** Fallo al guardar las filas descargadas en la BD local. */
    LOCAL
}

/**
 * [applied] son las filas remotas que se aplicaron localmente. Si [aborted] es true,
 * [applied] siempre es 0 -- la tabla es todo o nada (ver comentario en pullTable).
 *
 * [failure] es la causa cuando [aborted] es true (null = sin clasificar). [discarded] son las
 * filas descargadas que se DESCARTARON sin abortar la tabla porque su padre (deck/topic) no
 * existe en la BD local; un pull con descartes sigue contando como correcto. Ambos con valor
 * por defecto para no tocar a quien construye PullTableResult(applied, aborted).
 *
 * [downloaded] son las filas que bajo el servidor en esta tabla (0 si se aborto); [fullPull] es true si
 * la tabla se bajo ENTERA (sin cursor) y sin abortar; [fellBackToFullPull] es true si el servidor no
 * tiene server_updated_at y se hizo el pull completo clasico (no se avanza ninguna marca).
 */
data class PullTableResult(
    val applied: Int,
    val aborted: Boolean,
    val failure: PullFailure? = null,
    val discarded: Int = 0,
    val downloaded: Int = 0,
    val fullPull: Boolean = false,
    val fellBackToFullPull: Boolean = false
)

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
    private val backfillLocalDataUseCase: BackfillLocalDataUseCase,
    // Los tres con valor por defecto = comportamiento de siempre (pull completo en cada sync).
    private val watermarkStore: PullWatermarkStore = NoOpPullWatermarkStore,
    private val localTables: LocalTableInspector = NeverEmptyLocalTableInspector,
    private val clock: Clock = Clock.System
) {

    /** [forceFullPull] = ignorar las marcas guardadas y bajar las cuatro tablas enteras. */
    suspend operator fun invoke(forceFullPull: Boolean = false): SyncResult {
        val userId = currentUserId() ?: return SyncResult.NotAuthenticated
        // Unico sitio que llama al backfill (AuthViewModel ya no lo hace): cualquier
        // deck/topic/flashcard/scheduling creado con sesion ya activa nace con
        // userId = null -- nada mas lo asigna al crear. Sin este repaso aqui, esas
        // filas se quedan huerfanas para siempre: getPendingSync(userId) nunca las
        // ve porque su userId sigue siendo null. Idempotente (WHERE userId IS NULL),
        // asi que no hace dano llamarlo en cada sync.
        backfillLocalDataUseCase(userId)
        val pullResult = pull(userId, forceFullPull)
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
    //
    // Pull incremental: la marca (cursor por tabla + fecha del ultimo pull completo) se carga UNA
    // vez. Se baja TODO si se fuerza, si no hay fecha del ultimo completo, si pasaron
    // FULL_PULL_INTERVAL o si esa fecha esta en el futuro (reloj raro). Por tabla, el cursor
    // guardado se usa tal cual salvo pull completo o tabla local vacia.
    private suspend fun pull(userId: String, forceFullPull: Boolean): PullResult {
        val startedAt = clock.now()
        val marks = loadWatermarks(userId)
        val fullAll = forceFullPull || needsFullPull(marks, startedAt)

        fun cursorFor(table: SyncTable): PullCursor? =
            if (fullAll) null else marks?.cursors?.get(table)

        val decks = pullTable(
            userId, SyncTable.DECKS, cursorFor(SyncTable.DECKS),
            fetchSince = { remoteDataSource.pullDecksSince(userId, it) },
            fetchClassic = { remoteDataSource.pullDecks(userId, PULL_SINCE_EPOCH) },
            rowId = { it.id },
            apply = { PullApplyReport(decksRepository.applyRemotePull(it)) }
        )
        val topics = pullTable(
            userId, SyncTable.TOPICS, cursorFor(SyncTable.TOPICS),
            fetchSince = { remoteDataSource.pullTopicsSince(userId, it) },
            fetchClassic = { remoteDataSource.pullTopics(userId, PULL_SINCE_EPOCH) },
            rowId = { it.id },
            apply = { topicsRepository.applyRemotePullReporting(it) }
        )
        val flashcards = pullTable(
            userId, SyncTable.FLASHCARDS, cursorFor(SyncTable.FLASHCARDS),
            fetchSince = { remoteDataSource.pullFlashcardsSince(userId, it) },
            fetchClassic = { remoteDataSource.pullFlashcards(userId, PULL_SINCE_EPOCH) },
            rowId = { it.id },
            apply = { flashcardRepository.applyRemotePullReporting(it) }
        )
        val cardScheduling = pullTable(
            userId, SyncTable.CARD_SCHEDULING, cursorFor(SyncTable.CARD_SCHEDULING),
            fetchSince = { remoteDataSource.pullCardSchedulingSince(userId, it) },
            fetchClassic = { remoteDataSource.pullCardScheduling(userId, PULL_SINCE_EPOCH) },
            rowId = { it.cardId },
            apply = { cardSchedulingRepository.applyRemotePullReporting(it) }
        )
        val outcomes = listOf(decks, topics, flashcards, cardScheduling)

        // La fecha del ultimo pull completo solo se guarda si LAS CUATRO tablas se bajaron enteras y
        // bien. Lo bloquean los fallos (fetch, apply, fallback sin columna o filas sin cursor); los
        // descartes no (la fila reaparece sola porque el cursor no pasa de ella).
        if (outcomes.all { it.result.fullPull && !it.result.fellBackToFullPull && !it.cursorless }) {
            saveFullPullAt(userId, startedAt)
        }
        return PullResult(decks.result, topics.result, flashcards.result, cardScheduling.result)
    }

    private fun needsFullPull(marks: PullWatermarks?, now: Instant): Boolean {
        val lastFull = marks?.lastFullPullAt ?: return true
        return lastFull > now || now - lastFull >= FULL_PULL_INTERVAL
    }

    // El almacen no debe romper nunca el sync: cualquier fallo = "sin marca" = pull completo.
    private suspend fun loadWatermarks(userId: String): PullWatermarks? = try {
        watermarkStore.load(userId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun saveCursor(userId: String, table: SyncTable, cursor: PullCursor) {
        try {
            watermarkStore.saveCursor(userId, table, cursor)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // sin marca en la siguiente lectura = pull completo de esa tabla
        }
    }

    private suspend fun saveFullPullAt(userId: String, at: Instant) {
        try {
            watermarkStore.saveFullPullAt(userId, at)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // idem: sin fecha = pull completo en el siguiente sync
        }
    }

    // Si no se puede saber si la tabla local esta vacia, se baja entera (lo seguro).
    private suspend fun isLocalTableEmpty(table: SyncTable): Boolean = try {
        localTables.isEmpty(table)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        true
    }

    /** Resultado de una tabla + si sus filas llegaron sin cursor (RemoteDataSource antiguo/fake). */
    private class TableOutcome(val result: PullTableResult, val cursorless: Boolean)

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
     *
     * [storedCursor] es el cursor guardado de la tabla (null = pull completo). Con cursor pero tabla
     * local vacia (BD borrada o restaurada) esa tabla va completa. El cursor nuevo se guarda SOLO si
     * la tabla se bajo y aplico entera, y es siempre el de una fila que DEVOLVIO el servidor:
     * la ultima si no hubo descartes, o la inmediatamente anterior a la primera descartada (en el
     * orden del servidor) si los hubo.
     */
    private suspend fun <T> pullTable(
        userId: String,
        table: SyncTable,
        storedCursor: PullCursor?,
        fetchSince: suspend (PullCursor?) -> List<RemoteRow<T>>,
        fetchClassic: suspend () -> List<T>,
        rowId: (T) -> String,
        apply: suspend (List<T>) -> PullApplyReport
    ): TableOutcome {
        val cursor = if (storedCursor != null && isLocalTableEmpty(table)) null else storedCursor
        var fellBack = false
        var inApplyPhase = false
        return try {
            val remoteRows = try {
                fetchSince(cursor)
            } catch (e: MissingServerUpdatedAtException) {
                // Falta server_updated_at en el servidor: pull completo clasico, sin cursores. No es
                // un fallo de red ni avanza ninguna marca.
                fellBack = true
                fetchClassic().map { RemoteRow(it, null) }
            }
            inApplyPhase = true
            val report = apply(remoteRows.map { it.row })
            val newCursor = if (fellBack) null else nextCursor(remoteRows, report, rowId)
            if (newCursor != null) saveCursor(userId, table, newCursor)
            TableOutcome(
                PullTableResult(
                    applied = report.applied,
                    aborted = false,
                    discarded = report.discarded,
                    downloaded = remoteRows.size,
                    fullPull = cursor == null,
                    fellBackToFullPull = fellBack
                ),
                cursorless = remoteRows.isNotEmpty() && !fellBack && remoteRows.last().cursor == null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TableOutcome(
                PullTableResult(applied = 0, aborted = true, failure = classifyPullFailure(inApplyPhase, e)),
                cursorless = false
            )
        }
    }

    /**
     * Cursor a guardar tras un pull correcto, o null si no hay que cambiar nada (sin filas, o la
     * primera fila ya es una descartada, o el servidor no dio cursor). Nunca se calcula un maximo en
     * el cliente: es el cursor que el servidor dio a una de las filas.
     */
    private fun <T> nextCursor(
        rows: List<RemoteRow<T>>,
        report: PullApplyReport,
        rowId: (T) -> String
    ): PullCursor? {
        if (rows.isEmpty()) return null
        if (report.discardedIds.isEmpty()) return rows.last().cursor
        val discarded = report.discardedIds.toSet()
        val firstDiscarded = rows.indexOfFirst { rowId(it.row) in discarded }
        if (firstDiscarded < 0) return rows.last().cursor
        if (firstDiscarded == 0) return null
        return rows[firstDiscarded - 1].cursor
    }

    /**
     * Causa de un pull abortado (para que el mensaje al usuario no diga "sin conexion" cuando el
     * fallo fue local):
     * - cualquier excepcion al APLICAR las filas descargadas -> LOCAL;
     * - al DESCARGAR: timeout, transporte o 5xx -> NETWORK; un 4xx de PostgREST o de Auth (sesion
     *   o permisos, ambas son RestException) -> REJECTED;
     * - cualquier otra excepcion al descargar (p. ej. un error de decodificacion) -> null, sin
     *   clasificar: el mensaje la trata como red, igual que antes de existir esta clasificacion.
     */
    private fun classifyPullFailure(inApplyPhase: Boolean, e: Exception): PullFailure? = when {
        inApplyPhase -> PullFailure.LOCAL
        e.isNetworkOrServerFailure() -> PullFailure.NETWORK
        e is RestException && e.statusCode < 500 -> PullFailure.REJECTED
        else -> null
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
