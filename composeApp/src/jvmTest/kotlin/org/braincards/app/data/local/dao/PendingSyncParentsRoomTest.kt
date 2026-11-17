package org.braincards.app.data.local.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.data.local.AppDatabase
import org.braincards.app.data.local.entity.DeckEntity
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.local.entity.TopicEntity
import org.braincards.app.domain.models.CardType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Invariante de dueno del push de topics y flashcards (getPendingSyncTopics /
 * getPendingSyncFlashcards) contra una Room real: una fila solo es subible si su deck (y su topic,
 * si tiene) existe localmente y es del MISMO usuario. Los padres tombstone cuentan como
 * existentes, y NO se exige que el padre este ya sincronizado.
 */
class PendingSyncParentsRoomTest {

    private val me = "user-a"
    private val other = "user-b"
    private lateinit var db: AppDatabase

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun deck(
        id: String,
        owner: String? = me,
        deleted: Boolean = false,
        synced: Boolean = false
    ) = db.deckDao().addDeck(
        DeckEntity(
            id = id, name = id, createdAt = ms(1), updatedAt = ms(2), description = null,
            deletedAt = if (deleted) ms(2) else null, userId = owner, lastSyncedAt = if (synced) ms(2) else null
        )
    )

    private suspend fun topic(
        id: String,
        deckId: String,
        owner: String? = me,
        deleted: Boolean = false,
        synced: Boolean = false
    ) = db.topicDao().addTopic(
        TopicEntity(
            id = id, deckId = deckId, name = id, createdAt = ms(1), updatedAt = ms(2),
            deletedAt = if (deleted) ms(2) else null, userId = owner, lastSyncedAt = if (synced) ms(2) else null
        )
    )

    private suspend fun card(
        id: String,
        deckId: String,
        topicId: String? = null,
        owner: String? = me,
        synced: Boolean = false,
        updatedAt: Long = 2
    ) = db.flashcardDao().addFlashcard(
        FlashcardEntity(
            id = id, deckId = deckId, topicId = topicId, question = "Q", answer = "A", cardType = CardType.FLIP,
            createdAt = ms(1), updatedAt = ms(updatedAt), userId = owner, lastSyncedAt = if (synced) ms(2) else null
        )
    )

    private suspend fun pendingCards() = db.flashcardDao().getPendingSyncFlashcards(me).map { it.id }.sorted()
    private suspend fun pendingTopics() = db.topicDao().getPendingSyncTopics(me).map { it.id }.sorted()

    // --- flashcards: el deck ---

    @Test
    fun una_flashcard_con_deck_de_otro_usuario_no_es_subible() = runBlocking {
        deck("deck-ajeno", owner = other)
        card("c1", "deck-ajeno")

        assertEquals(emptyList(), pendingCards())
    }

    @Test
    fun una_flashcard_con_deck_propio_es_subible() = runBlocking {
        deck("deck-1")
        card("c1", "deck-1")

        assertEquals(listOf("c1"), pendingCards())
    }

    @Test
    fun una_flashcard_con_deck_tombstone_propio_es_subible_porque_el_deck_existe() = runBlocking {
        deck("deck-borrado", deleted = true)
        card("c1", "deck-borrado")

        assertEquals(listOf("c1"), pendingCards())
    }

    @Test
    fun una_flashcard_con_deck_propio_sin_sincronizar_es_subible_no_se_exige_lastSyncedAt_del_padre() = runBlocking {
        deck("deck-nuevo", synced = false)
        card("c1", "deck-nuevo")

        assertEquals(listOf("c1"), pendingCards())
    }

    // --- flashcards: el topic ---

    @Test
    fun una_flashcard_con_topic_de_otro_usuario_no_es_subible() = runBlocking {
        deck("deck-1")
        deck("deck-ajeno", owner = other)
        topic("topic-ajeno", "deck-ajeno", owner = other)
        card("c1", "deck-1", topicId = "topic-ajeno")

        assertEquals(emptyList(), pendingCards())
    }

    // 6b.3: una tarjeta ya sincronizada (con dueno) que se MUEVE a un tema recien creado con la sesion iniciada (userId
    // NULL, aun sin adoptar) no es subible hasta que el backfill (SyncEngineUseCase.invoke, ANTES del push) adopte el
    // tema; despues si. Fija el comportamiento del selector de tema del dialogo de tarjeta.
    @Test
    fun una_flashcard_movida_a_un_topic_aun_sin_dueno_se_sube_tras_adoptarlo() = runBlocking {
        deck("deck-1", synced = true)
        topic("topic-nuevo", "deck-1", owner = null)
        card("c1", "deck-1", topicId = "topic-nuevo", synced = true, updatedAt = 5) // editada (5) despues de sincronizar (2)

        assertEquals(emptyList(), pendingCards())

        db.topicDao().claimOrphanedTopics(me)

        assertEquals(listOf("c1"), pendingCards())
    }

    @Test
    fun una_flashcard_con_topic_propio_es_subible() = runBlocking {
        deck("deck-1")
        topic("topic-1", "deck-1")
        card("c1", "deck-1", topicId = "topic-1")

        assertEquals(listOf("c1"), pendingCards())
    }

    @Test
    fun una_flashcard_con_topic_tombstone_propio_es_subible_porque_el_topic_existe() = runBlocking {
        deck("deck-1")
        topic("topic-borrado", "deck-1", deleted = true)
        card("c1", "deck-1", topicId = "topic-borrado")

        assertEquals(listOf("c1"), pendingCards())
    }

    @Test
    fun una_flashcard_sin_topic_y_con_deck_propio_es_subible() = runBlocking {
        deck("deck-1")
        card("c1", "deck-1", topicId = null)

        assertEquals(listOf("c1"), pendingCards())
    }

    @Test
    fun solo_se_excluyen_las_flashcards_con_padre_ajeno_las_demas_siguen_pendientes() = runBlocking {
        deck("deck-1")
        deck("deck-ajeno", owner = other)
        card("buena", "deck-1")
        card("mala", "deck-ajeno")

        assertEquals(listOf("buena"), pendingCards())
    }

    // --- topics ---

    @Test
    fun un_topic_con_deck_de_otro_usuario_no_es_subible() = runBlocking {
        deck("deck-ajeno", owner = other)
        topic("t1", "deck-ajeno")

        assertEquals(emptyList(), pendingTopics())
    }

    @Test
    fun un_topic_con_deck_propio_es_subible() = runBlocking {
        deck("deck-1")
        topic("t1", "deck-1")

        assertEquals(listOf("t1"), pendingTopics())
    }

    @Test
    fun un_topic_con_deck_tombstone_propio_es_subible_porque_el_deck_existe() = runBlocking {
        deck("deck-borrado", deleted = true)
        topic("t1", "deck-borrado")

        assertEquals(listOf("t1"), pendingTopics())
    }

    @Test
    fun un_topic_con_deck_propio_sin_sincronizar_es_subible_no_se_exige_lastSyncedAt_del_padre() = runBlocking {
        deck("deck-nuevo", synced = false)
        topic("t1", "deck-nuevo")

        assertEquals(listOf("t1"), pendingTopics())
    }

    // --- la fila reaparece cuando el padre pasa a ser del mismo usuario ---

    @Test
    fun la_flashcard_reaparece_cuando_su_deck_pasa_a_ser_del_mismo_usuario() = runBlocking {
        deck("deck-1", owner = other)
        card("c1", "deck-1")
        assertEquals(emptyList(), pendingCards())

        deck("deck-1", owner = me) // REPLACE: el deck pasa a ser de "me"

        assertEquals(listOf("c1"), pendingCards())
    }

    @Test
    fun el_topic_reaparece_cuando_su_deck_pasa_a_ser_del_mismo_usuario() = runBlocking {
        deck("deck-1", owner = other)
        topic("t1", "deck-1")
        assertEquals(emptyList(), pendingTopics())

        deck("deck-1", owner = me)

        assertEquals(listOf("t1"), pendingTopics())
    }

    // --- las condiciones previas se mantienen ---

    @Test
    fun una_fila_ya_sincronizada_y_sin_cambios_no_es_pendiente_y_editada_despues_si() = runBlocking {
        deck("deck-1", synced = true)
        card("sin-cambios", "deck-1", synced = true, updatedAt = 2)
        card("editada", "deck-1", synced = true, updatedAt = 5)

        assertEquals(listOf("editada"), pendingCards())
    }

    @Test
    fun las_filas_de_otro_usuario_no_se_devuelven_aunque_sus_padres_sean_suyos() = runBlocking {
        deck("deck-ajeno", owner = other)
        topic("t-ajeno", "deck-ajeno", owner = other)
        card("c-ajena", "deck-ajeno", owner = other)

        assertEquals(emptyList(), pendingCards())
        assertEquals(emptyList(), pendingTopics())
    }
}
