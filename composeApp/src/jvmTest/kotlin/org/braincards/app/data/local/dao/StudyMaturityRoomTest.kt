package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.RoomTransactionRunner
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.local.entity.TopicEntity
import org.braincards.app.data.repository.DeckRepositoryImpl
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Madurez de las tarjetas (Estadisticas) contra una Room real en memoria, con foreign_keys = ON:
 * Nuevas = sin fila en card_scheduling; Aprendiendo = interval < 21; Maduras = interval >= 21; solo tarjetas ACTIVAS
 * (LIVE_DECK_JOIN). Siembra: mazo A vivo y mazo B en tombstone con tarjetas vivas.
 */
class StudyMaturityRoomTest {

    private lateinit var db: AppDatabase

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun deck(id: String, deletedAt: Long? = null) = db.deckDao().addDeck(
        DeckEntity(id = id, name = id, createdAt = ms(1), updatedAt = ms(deletedAt ?: 1), description = null,
            deletedAt = deletedAt?.let { ms(it) })
    )

    private suspend fun topic(id: String, deckId: String, deletedAt: Long? = null) = db.topicDao().addTopic(
        TopicEntity(id = id, deckId = deckId, name = id, createdAt = ms(1), updatedAt = ms(deletedAt ?: 1),
            deletedAt = deletedAt?.let { ms(it) })
    )

    /** [interval] null = sin fila de scheduling (nunca repasada). */
    private suspend fun card(
        id: String, deckId: String, interval: Int?, topicId: String? = null, deleted: Boolean = false
    ) {
        db.flashcardDao().addFlashcard(
            FlashcardEntity(id = id, deckId = deckId, topicId = topicId, question = "Q$id", answer = "A$id",
                cardType = CardType.FLIP, createdAt = ms(1), updatedAt = ms(1), deletedAt = if (deleted) ms(2) else null)
        )
        if (interval != null) schedule(id, interval)
    }

    private suspend fun schedule(cardId: String, interval: Int) = db.cardSchedulingDao().insert(
        CardSchedulingEntity(cardId = cardId, easeFactor = 2.5, interval = interval, repetitions = if (interval == 0) 0 else 1,
            nextReview = ms(99_000), updatedAt = ms(1), userId = null, lastSyncedAt = null)
    )

    private suspend fun seed() {
        deck("A")
        topic("tX", "A", deletedAt = 3)                    // tema borrado: su tarjeta (mazo vivo) sigue contando
        card("n1", "A", interval = null)                   // Nueva
        card("n2", "A", interval = null, topicId = "tX")   // Nueva (tema borrado)
        card("l0", "A", interval = 0)                      // Aprendiendo: recien fallada (intervalo 0)
        card("l20", "A", interval = 20)                    // Aprendiendo: justo por debajo del umbral
        card("m21", "A", interval = 21)                    // Madura: justo en el umbral
        card("m400", "A", interval = 400)                  // Madura
        card("borrada", "A", interval = 30, deleted = true) // borrada con scheduling: no cuenta (fila de scheduling huerfana)

        deck("B", deletedAt = 4)                           // mazo en tombstone con tarjetas VIVAS
        card("b1", "B", interval = null)
        card("b2", "B", interval = 5)
        card("b3", "B", interval = 50)
    }

    private suspend fun counts() = db.flashcardDao().observeMaturityCounts().first()

    private fun row(n: Int, l: Int, m: Int) = MaturityCountsRow(newCards = n, learningCards = l, matureCards = m)

    // ---- la consulta ----

    @Test
    fun cuenta_nuevas_aprendiendo_y_maduras_sobre_tarjetas_activas() = runBlocking<Unit> {
        seed()
        assertEquals(row(n = 2, l = 2, m = 2), counts())
    }

    // Mutacion M2: con `<= 21` en vez de `< 21`, la tarjeta de intervalo 21 saldria como Aprendiendo.
    @Test
    fun la_frontera_es_20_aprendiendo_y_21_madura() = runBlocking<Unit> {
        deck("A")
        card("c20", "A", interval = 20)
        assertEquals(row(0, 1, 0), counts())
        card("c21", "A", interval = 21)
        assertEquals(row(0, 1, 1), counts())
    }

    // Mutacion M3: si "Nueva" se decidiera por repetitions == 0 (definicion antigua) en vez de "sin fila", fallaria.
    @Test
    fun una_tarjeta_recien_fallada_con_intervalo_0_es_Aprendiendo_no_Nueva() = runBlocking<Unit> {
        deck("A")
        card("f", "A", interval = 0)
        assertEquals(row(0, 1, 0), counts())
    }

    @Test
    fun estudiar_una_nueva_la_mueve_de_Nuevas_a_Aprendiendo() = runBlocking<Unit> {
        deck("A")
        card("c", "A", interval = null)
        assertEquals(row(1, 0, 0), counts())
        schedule("c", interval = 1)
        assertEquals(row(0, 1, 0), counts())
    }

    // Mutacion M1: si la consulta no usara LIVE_DECK_JOIN, las 3 tarjetas del mazo en tombstone contarian (3/3/3 extra).
    @Test
    fun las_tarjetas_de_un_mazo_en_tombstone_no_cuentan() = runBlocking<Unit> {
        deck("B", deletedAt = 4)
        card("b1", "B", interval = null)
        card("b2", "B", interval = 5)
        card("b3", "B", interval = 50)
        assertEquals(row(0, 0, 0), counts())
    }

    @Test
    fun una_fila_de_scheduling_de_una_tarjeta_borrada_no_cuenta() = runBlocking<Unit> {
        deck("A")
        card("borrada", "A", interval = 30, deleted = true)
        assertEquals(row(0, 0, 0), counts())
    }

    @Test
    fun una_base_vacia_o_sin_mazos_da_cero_sin_nulos() = runBlocking<Unit> {
        assertEquals(row(0, 0, 0), counts())   // SUM sobre cero filas es NULL: COALESCE lo deja en 0
        deck("A")
        assertEquals(row(0, 0, 0), counts())   // mazo sin tarjetas
    }

    // ---- paridad con los datos en bruto (el oraculo) ----

    /** Misma definicion calculada en memoria a partir de getAllFlashcards() y card_scheduling.getAll(). */
    private suspend fun oracle(): MaturityCountsRow {
        val cards = db.flashcardDao().getAllFlashcards().first()
        val byCard = db.cardSchedulingDao().getAll().first().associateBy { it.cardId }
        return MaturityCountsRow(
            newCards = cards.count { byCard[it.id] == null },
            learningCards = cards.count { (byCard[it.id]?.interval ?: Int.MAX_VALUE) < 21 && byCard[it.id] != null },
            matureCards = cards.count { (byCard[it.id]?.interval ?: -1) >= 21 }
        )
    }

    @Test
    fun la_consulta_coincide_con_el_calculo_en_memoria_en_cada_paso() = runBlocking<Unit> {
        assertEquals(oracle(), counts())
        seed()
        assertEquals(oracle(), counts())
        card("x1", "A", interval = 21)
        card("x2", "A", interval = null)
        assertEquals(oracle(), counts())
        deck("C")
        card("c1", "C", interval = 7)
        schedule("n1", interval = 3)
        assertEquals(oracle(), counts())
        assertEquals(row(n = 2, l = 4, m = 3), counts()) // n1 pasa a Aprendiendo: 2 nuevas (n2, x2), 4 aprendiendo, 3 maduras
    }

    // ---- reactividad y mazo que resucita ----

    @Test
    fun el_flujo_se_reemite_al_estudiar_una_tarjeta() = runBlocking<Unit> {
        deck("A")
        card("c", "A", interval = null)
        coroutineScope {
            val values = CopyOnWriteArrayList<MaturityCountsRow>()
            val job = launch(Dispatchers.Default) { db.flashcardDao().observeMaturityCounts().collect { values.add(it) } }
            try {
                withTimeout(5_000) { while (values.lastOrNull() != row(1, 0, 0)) delay(10) }
                schedule("c", interval = 30)
                withTimeout(5_000) { while (values.lastOrNull() != row(0, 0, 1)) delay(10) }
            } finally {
                job.cancel()
            }
        }
    }

    @Test
    fun un_mazo_en_tombstone_que_vuelve_a_estar_vivo_por_pull_recupera_sus_tarjetas() = runBlocking<Unit> {
        seed()
        val decks = DeckRepositoryImpl(db.deckDao(), db.topicDao(), db.flashcardDao(), RoomTransactionRunner(db))
        assertEquals(row(2, 2, 2), counts())

        decks.applyRemotePull(listOf(Deck(id = "B", name = "B", createdAt = ms(1), updatedAt = ms(50), description = null, deletedAt = null)))

        assertEquals(row(3, 3, 3), counts()) // b1 nueva, b2 (5 dias) aprendiendo, b3 (50 dias) madura
    }
}
