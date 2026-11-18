package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.repository.CardSchedulingRepositoryImpl
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.data.repository.FlashcardRepositoryImpl
import org.braincards.app.data.repository.FreeReviewCardsSourceImpl
import org.braincards.app.data.repository.ReviewLogRepositoryImpl
import org.braincards.app.data.repository.TopicRepositoryImpl
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.models.ReviewQuality.AGAIN
import org.braincards.app.domain.models.ReviewQuality.GOOD
import org.braincards.app.domain.models.StudyMode
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.domain.usecase.Flashcards.GetDueFlashcardsUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardByIdUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFreeReviewCardsUseCase
import org.braincards.app.domain.usecase.Flashcards.ResolveStudyLabelUseCase
import org.braincards.app.domain.usecase.Flashcards.ReviewFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.ScheduledRecorder
import org.braincards.app.presentation.screens.study.StudyState
import org.braincards.app.presentation.screens.study.StudyViewModel
import org.braincards.app.presentation.sync.AutoSyncPauseGate
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * INVARIANTE CLAVE del paso 14, a nivel Room: una sesion de REPASO LIBRE completa (con falladas re-encoladas) deja TODAS las tablas
 * exactamente como estaban: `card_scheduling` identica (ni filas nuevas ni cambios), `reviewlog` sin una sola fila, las tarjetas sin
 * tocar (`updatedAt` incluido) y NADA pendiente de sincronizar. El CONTROL negativo ejecuta el mismo flujo en modo normal y comprueba que
 * ahi SI escribe: asi el test no puede estar ciego.
 *
 * Siembra: a1 con scheduling vencido, a2 SIN scheduling, a3 con scheduling futuro (no pendiente), todas del usuario "u" ya sincronizadas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FreeReviewNoWritesRoomTest {

    private lateinit var db: AppDatabase

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
        runBlocking { seed() }
    }

    @AfterTest
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun seed() {
        db.deckDao().addDeck(
            DeckEntity(id = "D", name = "D", createdAt = ms(1), updatedAt = ms(1), description = null, deletedAt = null,
                userId = "u", lastSyncedAt = ms(10))
        )
        suspend fun card(id: String, nextReview: Long?) = RoomTransactionRunner(db).inTransaction {
            db.flashcardDao().addFlashcard(
                FlashcardEntity(id = id, deckId = "D", topicId = null, question = "Q$id", answer = "A$id", cardType = CardType.FLIP,
                    createdAt = ms(1), updatedAt = ms(1), deletedAt = null, userId = "u", lastSyncedAt = ms(10))
            )
            if (nextReview != null) {
                db.cardSchedulingDao().insert(
                    CardSchedulingEntity(cardId = id, easeFactor = 2.5, interval = 3, repetitions = 2, nextReview = ms(nextReview),
                        updatedAt = ms(1), userId = "u", lastSyncedAt = ms(10))
                )
            }
        }
        card("a1", nextReview = 5_000)
        card("a2", nextReview = null)
        card("a3", nextReview = 9_000_000_000_000)
    }

    private val flashcardRepo get() = FlashcardRepositoryImpl(db.flashcardDao())
    private val schedulingRepo get() = CardSchedulingRepositoryImpl(db.cardSchedulingDao(), RoomTransactionRunner(db))

    private fun newViewModel() = StudyViewModel(
        getDueFlashcardsUseCase = GetDueFlashcardsUseCase(flashcardRepo),
        getFreeReviewCardsUseCase = GetFreeReviewCardsUseCase(FreeReviewCardsSourceImpl(db.flashcardDao())),
        scheduledRecorder = ScheduledRecorder(ReviewFlashcardUseCase(ReviewLogRepositoryImpl(db.reviewLogDao()), schedulingRepo)),
        getFlashcardByIdUseCase = GetFlashcardByIdUseCase(flashcardRepo),
        // Repositorios REALES de mazos y temas (la etiqueta lee la base de verdad): cualquier escritura en ellos seria visible.
        resolveStudyLabelUseCase = ResolveStudyLabelUseCase(
            DeckRepositoryImpl(db.deckDao(), db.topicDao(), db.flashcardDao(), RoomTransactionRunner(db)),
            TopicRepositoryImpl(db.topicDao(), db.flashcardDao(), RoomTransactionRunner(db))
        ),
        syncPauseGate = AutoSyncPauseGate()
    )

    /** Foto de TODO lo que el repaso libre no debe tocar. */
    private data class Snapshot(
        val scheduling: List<CardSchedulingEntity>,
        val flashcards: List<FlashcardEntity>,
        val reviewLogRows: Long,
        val pendingScheduling: Int,
        val pendingFlashcards: Int,
        /** VOLCADO de TODAS las tablas de la base (nombre -> filas ordenadas): cualquier escritura en cualquier tabla cambia esto. */
        val allTables: Map<String, List<String>>
    ) {
        /** Huella del volcado completo (para el informe y los mensajes). */
        val hash: String
            get() = MessageDigest.getInstance("SHA-256").digest(allTables.toSortedMap().toString().toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

        /** Tablas cuyo contenido difiere de [other]. */
        fun changedTables(other: Snapshot): Set<String> =
            (allTables.keys + other.allTables.keys).filter { allTables[it] != other.allTables[it] }.toSet()
    }

    /** Volcado de todas las tablas de usuario (sqlite_*, room_* y android_metadata fuera): cada fila como texto, filas ordenadas. */
    private suspend fun dumpAllTables(): Map<String, List<String>> = db.useReaderConnection { c ->
        val tables = c.usePrepared(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' " +
                "AND name <> 'android_metadata' ORDER BY name"
        ) { st -> buildList { while (st.step()) add(st.getText(0)) } }
        tables.associateWith { table ->
            c.usePrepared("SELECT * FROM \"$table\"") { st ->
                val n = st.getColumnCount()
                buildList {
                    while (st.step()) add((0 until n).joinToString("|") { i -> if (st.isNull(i)) "NULL" else st.getText(i) })
                }.sorted()
            }
        }
    }

    private suspend fun snapshot() = Snapshot(
        scheduling = db.cardSchedulingDao().getAll().first().sortedBy { it.cardId },
        flashcards = db.flashcardDao().getFreeReviewCards(),
        reviewLogRows = db.useReaderConnection { c -> c.usePrepared("SELECT COUNT(*) FROM reviewlog") { it.step(); it.getLong(0) } },
        pendingScheduling = schedulingRepo.getPendingSync("u").size,
        pendingFlashcards = flashcardRepo.getPendingSync("u").size,
        allTables = dumpAllTables()
    )

    private suspend fun StudyViewModel.awaitReviewing(): StudyState.Reviewing =
        withTimeout(10_000) { state.first { it is StudyState.Reviewing } as StudyState.Reviewing }

    /** Puntua la tarjeta en pantalla y espera a que cambie el estado (reintenta si el toque cae mientras se avanza: se ignora). */
    private suspend fun StudyViewModel.rateAndAwait(q: ReviewQuality) {
        val before = state.value
        repeat(200) {
            showAnswer()
            rateCard(q)
            val now = state.value
            if (now != before) return
            delay(10)
        }
        error("el estado no cambio tras puntuar")
    }

    private suspend fun runWholeSession(vm: StudyViewModel, firstFails: Boolean = true) {
        vm.awaitReviewing()
        if (firstFails) vm.rateAndAwait(AGAIN) // la fallada se re-encola y se vuelve a ver
        while (vm.state.value is StudyState.Reviewing) vm.rateAndAwait(GOOD)
        withTimeout(10_000) { vm.state.first { it is StudyState.Finished } }
    }

    @Test
    fun una_sesion_libre_completa_deja_TODAS_las_tablas_exactamente_como_estaban() = runBlocking {
        val before = snapshot()
        assertEquals(0, before.reviewLogRows)
        assertEquals(0, before.pendingScheduling)
        assertEquals(0, before.pendingFlashcards)

        val vm = newViewModel()
        vm.initSession(StudyScope.All, StudyMode.Free(seed = 3L))
        assertEquals(3, vm.awaitReviewing().total, "usa las 3 tarjetas, tambien la FUTURA (a3) y la que no tiene scheduling (a2)")
        runWholeSession(vm)

        val done = vm.state.value as StudyState.Finished
        assertTrue(done.freeReview)
        assertEquals(4, done.results.totalReviews, "se contaron en memoria: 3 tarjetas + 1 repeticion de la fallada")

        val after = snapshot()
        assertEquals(before.scheduling, after.scheduling, "card_scheduling IDENTICA: ni filas nuevas ni cambios")
        assertEquals(3 - 1, after.scheduling.size, "a2 sigue sin fila de scheduling (no se crea)")
        assertEquals(0, after.reviewLogRows, "reviewlog sin una sola fila")
        assertEquals(before.flashcards, after.flashcards, "las tarjetas no se tocan (updatedAt incluido)")
        assertEquals(0, after.pendingScheduling, "nada que sincronizar: programacion")
        assertEquals(0, after.pendingFlashcards, "nada que sincronizar: tarjetas")
        // TODAS las tablas de la base, no solo las tres de arriba: ni una fila cambia en ninguna.
        assertTrue(after.allTables.keys.containsAll(setOf("decks", "topics", "flashcards", "card_scheduling", "reviewlog")),
            "el volcado cubre las tablas esperadas: ${after.allTables.keys}")
        assertEquals(emptySet(), before.changedTables(after), "tablas modificadas por el repaso libre")
        assertEquals(before.hash, after.hash, "huella de la base completa antes y despues")
    }

    @Test
    fun una_sesion_libre_de_un_solo_mazo_tampoco_escribe() = runBlocking {
        val before = snapshot()
        val vm = newViewModel()
        vm.initSession(StudyScope.Deck("D"), StudyMode.Free(seed = 9L))
        runWholeSession(vm, firstFails = false)
        val after = snapshot()
        assertEquals(before, after)
        assertEquals(before.hash, after.hash)
    }

    @Test
    fun CONTROL_la_misma_sesion_en_modo_normal_SI_escribe_en_programacion_e_historial() = runBlocking {
        val before = snapshot()

        val vm = newViewModel()
        vm.initSession(StudyScope.All, StudyMode.Scheduled)
        // pendientes: a1 (vencida) y a2 (sin scheduling); a3 es futura y no entra
        assertEquals(2, vm.awaitReviewing().total)
        runWholeSession(vm, firstFails = false)

        val after = snapshot()
        assertEquals(2, after.reviewLogRows, "dos puntuaciones = dos filas de historial")
        assertNotEquals(before.scheduling, after.scheduling, "la programacion SI cambia (y a2 gana su fila)")
        assertEquals(3, after.scheduling.size)
        assertEquals(setOf("card_scheduling", "reviewlog"), before.changedTables(after), "y solo esas dos tablas")
        assertNotEquals(before.hash, after.hash)
    }
}
