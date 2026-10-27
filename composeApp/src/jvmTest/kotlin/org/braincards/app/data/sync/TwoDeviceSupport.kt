package org.braincards.app.data.sync

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.braincards.app.data.fakes.FakeSettingsDataStore
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.DataStorePullWatermarkStore
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.mapper.toDto
import org.braincards.app.data.remote.RemoteDataSourceImpl
import org.braincards.app.data.remote.StubHttpEngine
import org.braincards.app.data.remote.stubRemote
import org.braincards.app.data.remote.table
import org.braincards.app.data.remote.dto.CardSchedulingDto
import org.braincards.app.data.remote.dto.DeckDto
import org.braincards.app.data.remote.dto.FlashcardDto
import org.braincards.app.data.repository.CardSchedulingRepositoryImpl
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.FlashcardRepositoryImpl
import org.braincards.app.data.repository.LocalDataRepositoryImpl
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import org.braincards.app.domain.usecase.Sync.SyncResult
import org.braincards.app.presentation.sync.SyncCoordinator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Reloj controlable desde el test. */
class MutableClock(var now: Instant) : Clock {
    override fun now(): Instant = now
    operator fun plusAssign(d: Duration) { now += d }
}

/** Una peticion GET servida por el servidor falso: de que dispositivo, que tabla y cuantas filas devolvio. */
data class ServedGet(val device: String, val table: String, val rows: Int, val params: Map<String, String>)

/**
 * Servidor PostgREST falso y COMPARTIDO por los dispositivos: tablas en memoria que entienden el
 * subconjunto de la API que usa RemoteDataSourceImpl (GET con eq / gte / or+and / order / limit /
 * offset, y POST upsert). Sella `server_updated_at` con SU PROPIO reloj ([clock]), independiente de
 * los relojes de los dispositivos, como hace el trigger. Cada sello avanza el reloj [autoTick] (0 =
 * empates de marca). Cualquier parametro que no entienda hace fallar la peticion en voz alta.
 */
class FakeServer(start: Instant, var autoTick: Duration = 1.minutes) {
    val clock = MutableClock(start)
    val gets = mutableListOf<ServedGet>()

    /** Se llama DESPUES de servir cada GET (dispositivo, tabla, n.o de GET de esa tabla): escrituras durante la descarga. */
    var afterGet: ((table: String, getIndex: Int) -> Unit)? = null

    private val tables = mapOf(
        "decks" to linkedMapOf<String, JsonObject>(),
        "topics" to linkedMapOf(),
        "flashcards" to linkedMapOf(),
        "card_scheduling" to linkedMapOf()
    )
    private val getCount = mutableMapOf<String, Int>()
    private val json = Json { encodeDefaults = true }

    private fun pkOf(table: String) = if (table == "card_scheduling") "card_id" else "id"

    private fun stamp(): Instant {
        val s = clock.now
        clock.now += autoTick
        return s
    }

    private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun stampOf(row: JsonObject): Instant = Instant.parse(row.str("server_updated_at")!!)

    private fun store(table: String, row: JsonObject) {
        val withStamp = JsonObject(row + ("server_updated_at" to JsonPrimitive(stamp().toString().replace("Z", "+00:00"))))
        tables.getValue(table)[withStamp.str(pkOf(table))!!] = withStamp
    }

    // --- API directa para los tests (escrituras "de otro cliente" sin pasar por un dispositivo) -------------------

    fun putDeck(deck: Deck) = store("decks", json.encodeToJsonElement(DeckDto.serializer(), deck.toDto()).jsonObject)
    fun putFlashcard(card: Flashcard) = store("flashcards", json.encodeToJsonElement(FlashcardDto.serializer(), card.toDto()).jsonObject)
    fun putScheduling(s: CardScheduling) = store("card_scheduling", json.encodeToJsonElement(CardSchedulingDto.serializer(), s.toDto()).jsonObject)

    fun stampOf(table: String, id: String): Instant = stampOf(tables.getValue(table).getValue(id))
    fun rowCount(table: String) = tables.getValue(table).size
    fun nameOf(table: String, id: String): String? = tables.getValue(table)[id]?.str("name")

    // --- manejador HTTP ------------------------------------------------------------------------------------------------

    fun handle(request: HttpRequestData, device: String): Pair<HttpStatusCode, String> {
        val table = request.table()
        require(table in tables) { "tabla desconocida: $table" }
        return when (request.method) {
            HttpMethod.Get -> get(request, table, device)
            HttpMethod.Post -> post(request, table)
            else -> error("metodo no soportado: ${request.method}")
        }
    }

    private val orRegex = Regex("""^\((\w+)\.gt\.([^,]+),and\((\w+)\.eq\.([^,]+),(\w+)\.(gt|lt)\.(.*)\)\)$""")

    // Columnas de marca que entiende el servidor falso: la de verdad y `updated_at` (para poder
    // ejecutar tambien una version defectuosa del cliente que filtre por la hora del dispositivo).
    private val tsColumns = listOf("server_updated_at", "updated_at")

    private fun tsOf(row: JsonObject, col: String): Instant = Instant.parse(row.str(col)!!)

    private fun get(request: HttpRequestData, table: String, device: String): Pair<HttpStatusCode, String> {
        val p = request.url.parameters
        val pk = pkOf(table)
        val unknown = p.names() - setOf("select", "user_id", "or", "order", "limit", "offset") - tsColumns.toSet()
        check(unknown.isEmpty()) { "parametros no soportados por el servidor falso: $unknown" }

        val user = p["user_id"]!!.removePrefix("eq.")
        var rows = tables.getValue(table).values.filter { it.str("user_id") == user }
        for (col in tsColumns) {
            p[col]?.let { v ->
                check(v.startsWith("gte.")) { "filtro de marca inesperado: $v" }
                val ts = Instant.parse(v.removePrefix("gte."))
                rows = rows.filter { tsOf(it, col) >= ts }
            }
        }
        p["or"]?.let { v ->
            val m = orRegex.matchEntire(v) ?: error("filtro or inesperado: $v")
            val (gtCol, gtTs, eqCol, eqTs, keyCol, keyOp, id) = m.destructured
            check(gtTs == eqTs && gtCol == eqCol && gtCol in tsColumns && keyCol == pk) { "or incoherente: $v" }
            val ts = Instant.parse(gtTs)
            rows = rows.filter { val s = tsOf(it, gtCol); s > ts || (s == ts && (if (keyOp == "gt") it.str(pk)!! > id else it.str(pk)!! < id)) }
        }
        val order = (p["order"] ?: "server_updated_at.asc.nullslast,$pk.asc.nullslast").split(",").map {
            val parts = it.split(".")
            parts[0] to (parts[1] == "asc")
        }
        rows = rows.sortedWith { a, b ->
            var c = 0
            for ((col, asc) in order) {
                c = if (col in tsColumns) tsOf(a, col).compareTo(tsOf(b, col)) else a.str(col)!!.compareTo(b.str(col)!!)
                if (!asc) c = -c
                if (c != 0) break
            }
            c
        }
        p["offset"]?.let { rows = rows.drop(it.toInt()) }
        p["limit"]?.let { rows = rows.take(it.toInt()) }

        gets += ServedGet(device, table, rows.size, p.names().associateWith { p[it]!! })
        val index = (getCount[table] ?: 0) + 1
        getCount[table] = index
        val body = JsonArray(rows).toString()
        afterGet?.invoke(table, index)
        return HttpStatusCode.OK to body
    }

    private fun post(request: HttpRequestData, table: String): Pair<HttpStatusCode, String> {
        val bytes = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        val items: List<JsonElement> = Json.parseToJsonElement(bytes).jsonArray
        items.forEach { store(table, it.jsonObject) }
        return HttpStatusCode.Created to ""
    }
}

/**
 * Un dispositivo: su Room real en memoria, su motor (con el almacen de marcas sobre un DataStore en
 * memoria y LocalDataRepositoryImpl real como LocalTableInspector), su reloj y su RemoteDataSourceImpl
 * REAL hablando con el [FakeServer] a traves de un motor Ktor falso.
 */
class Device(
    val name: String,
    server: FakeServer,
    var userId: String,
    val clock: MutableClock,
    pageSize: Int = 2
) {
    val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
    val auth = FakeAuthRepository(AuthState.Authenticated(userId, "$userId@x.com", sessionConfirmed = true))
    val dataStore = FakeSettingsDataStore()
    val store = DataStorePullWatermarkStore(dataStore)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    val decks = DeckRepositoryImpl(db.deckDao(), db.topicDao(), db.flashcardDao())
    val topics = TopicRepositoryImpl(db.topicDao(), db.flashcardDao())
    val flashcards = FlashcardRepositoryImpl(db.flashcardDao())
    val scheduling = CardSchedulingRepositoryImpl(db.cardSchedulingDao(), RoomTransactionRunner(db))
    val localData = LocalDataRepositoryImpl(
        deckDao = db.deckDao(), topicDao = db.topicDao(), flashcardDao = db.flashcardDao(),
        cardSchedulingDao = db.cardSchedulingDao(), reviewLogDao = db.reviewLogDao(),
        transactionRunner = RoomTransactionRunner(db)
    )

    private val remotePair: Pair<RemoteDataSourceImpl, StubHttpEngine> =
        stubRemote(pageSize) { request -> server.handle(request, name) }
    internal val http: StubHttpEngine = remotePair.second

    val engine = SyncEngineUseCase(
        authRepository = auth,
        remoteDataSource = remotePair.first,
        decksRepository = decks,
        topicsRepository = topics,
        flashcardRepository = flashcards,
        cardSchedulingRepository = scheduling,
        backfillLocalDataUseCase = BackfillLocalDataUseCase(decks, topics, flashcards, scheduling),
        watermarkStore = store,
        localTables = localData,
        clock = clock
    )

    // Perezoso: SyncCoordinator sincroniza por su cuenta al ver una sesion autenticada, asi que solo se
    // construye en los tests de cierre de sesion (el resto llama al motor directamente, en el momento exacto).
    val coordinator: SyncCoordinator by lazy { buildCoordinator() }

    private fun buildCoordinator() = SyncCoordinator(
        authRepository = auth,
        syncEngineUseCase = engine,
        decksRepository = decks,
        topicsRepository = topics,
        flashcardRepository = flashcards,
        cardSchedulingRepository = scheduling,
        dataStore = dataStore,
        localDataRepository = localData,
        signOutAndWipeUseCase = SignOutAndWipeUseCase(auth, localData),
        coroutineScope = scope
    )

    fun close() {
        scope.cancel()
        db.close()
    }

    suspend fun sync(force: Boolean = false): SyncResult.Completed = engine(force) as SyncResult.Completed

    fun signInAs(newUser: String) {
        userId = newUser
        auth.setState(AuthState.Authenticated(newUser, "$newUser@x.com", sessionConfirmed = true))
    }

    /** Peticiones GET que ESTE dispositivo ha hecho a [table], de la mas antigua a la mas reciente. */
    internal fun getsTo(table: String): List<HttpRequestData> =
        http.requests.filter { it.method == HttpMethod.Get && it.table() == table }

    // --- datos locales --------------------------------------------------------------------------------------------

    private val t0 get() = clock.now()

    suspend fun addDeck(id: String, name: String = "Deck $id", at: Instant = t0) =
        decks.addDeck(Deck(id, name, at, at, null, null, userId, null))

    suspend fun editDeck(id: String, name: String, at: Instant = t0) {
        val d = decks.getDeckById(id)!!
        decks.updateDeck(d.copy(name = name, updatedAt = at))
    }

    /** Borrado logico con la hora DEL DISPOSITIVO (deleteDeck del repositorio usa el reloj real). */
    suspend fun tombstoneDeck(id: String, at: Instant = t0) {
        val d = decks.getDeckById(id)!!
        decks.updateDeck(d.copy(deletedAt = at, updatedAt = at))
    }

    suspend fun addCard(id: String, deckId: String, at: Instant = t0, topicId: String? = null) =
        flashcards.addFlashcard(Flashcard(id, deckId, "Q$id", "A$id", at, at, CardType.FLIP, topicId, null, null, userId, null, null))

    suspend fun addTopic(id: String, deckId: String, at: Instant = t0) =
        topics.addTopic(Topic(id, deckId, "Topic $id", at, at, null, null, userId))

    suspend fun addScheduling(cardId: String, at: Instant = t0) =
        scheduling.upsert(CardScheduling(cardId, 2.5, 1, 1, at, at, userId, null))

    private suspend fun <R> query(sql: String, read: (androidx.sqlite.SQLiteStatement) -> R): R? =
        db.useWriterConnection { tx -> tx.usePrepared(sql) { st -> if (st.step()) read(st) else null } }

    suspend fun count(table: String): Long = query("SELECT COUNT(*) FROM \"$table\"") { it.getLong(0) }!!

    suspend fun deckName(id: String): String? = query("SELECT name FROM decks WHERE id = '$id'") { it.getText(0) }

    suspend fun deckIsTombstone(id: String): Boolean? =
        query("SELECT deletedAt IS NOT NULL FROM decks WHERE id = '$id'") { it.getLong(0) == 1L }

    suspend fun hasCard(id: String): Boolean = query("SELECT 1 FROM flashcards WHERE id = '$id'") { true } ?: false
}
