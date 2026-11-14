package org.braincards.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import org.braincards.app.data.local.entity.FlashcardEntity

@Dao
interface FlashcardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFlashcard(flashcard: FlashcardEntity)

    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)


    @Query("SELECT * FROM flashcards WHERE id = :id AND deletedAt IS NULL")
    suspend fun getFlashcardById(id: String): FlashcardEntity?

    // sync (Bloque E, LWW) -- ver comentario en DeckDao.getDeckByIdIncludingDeleted.
    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getFlashcardByIdIncludingDeleted(id: String): FlashcardEntity?

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId AND deletedAt IS NULL")
    fun getFlashcardsByDeckId(deckId: String): Flow<List<FlashcardEntity>>

    // updatedAt tambien se toca al borrar -- ver mismo comentario en DeckDao.softdeleteDeck.
    @Query("UPDATE flashcards SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softdeleteFlashcardById(id: String, deletedAt: Instant)

    // nextReview vive en card_scheduling desde que se separo el scheduling de Flashcard;
    // "due" ahora se resuelve con un LEFT JOIN, seleccionando solo columnas de flashcards.
    // LEFT JOIN + COALESCE: una tarjeta que nunca se ha repasado no tiene fila en
    // card_scheduling (se crea en el primer repaso) y debe contar como vencida. El 0 es
    // epoch en milisegundos, la misma unidad en la que Converters guarda el Instant.
    @Query(
        "SELECT flashcards.* FROM flashcards " +
            "LEFT JOIN card_scheduling ON flashcards.id = card_scheduling.cardId " +
            "WHERE flashcards.deckId = :deckId AND flashcards.deletedAt IS NULL " +
            "AND COALESCE(card_scheduling.nextReview, 0) <= :now " +
            "ORDER BY COALESCE(card_scheduling.nextReview, 0) ASC"
    )
    suspend fun getDueFlashcardsByDeck(deckId: String, now: Instant): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE deletedAt IS NULL")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>


    @Query(
        "SELECT flashcards.* FROM flashcards " +
            "LEFT JOIN card_scheduling ON flashcards.id = card_scheduling.cardId " +
            "WHERE flashcards.deletedAt IS NULL AND COALESCE(card_scheduling.nextReview, 0) <= :now " +
            "ORDER BY COALESCE(card_scheduling.nextReview, 0) ASC"
    )
    suspend fun getAllDueFlashcards(now: Instant): List<FlashcardEntity>

    // Home: tarjetas pendientes de repaso, alcance "todas". DEBE mantener el mismo FROM/WHERE que getAllDueFlashcards
    // (LEFT JOIN + deletedAt IS NULL + COALESCE(nextReview, 0) <= :now): es el criterio de la sesion de estudio
    // (StudyScope.All). Un test de propiedad (DueCountRoomTest) compara ambos.
    @Query(
        "SELECT COUNT(*) FROM flashcards " +
            "LEFT JOIN card_scheduling ON flashcards.id = card_scheduling.cardId " +
            "WHERE flashcards.deletedAt IS NULL AND COALESCE(card_scheduling.nextReview, 0) <= :now"
    )
    fun observeDueCount(now: Instant): Flow<Int>

    @Query("UPDATE flashcards SET userId = :userId WHERE userId IS NULL")
    suspend fun claimOrphanedFlashcards(userId: String): Int

    // sync (Bloque D) -- mismo criterio que DeckDao/TopicDao, mas el invariante de dueno: solo se
    // sube una flashcard cuyo deck (y su topic, si tiene) existen localmente (tambien borrados
    // logicamente) y son del MISMO usuario. El INSERT del servidor solo comprueba
    // user_id = auth.uid() y la FK salta RLS: sin esto se subirian flashcards colgadas del
    // deck/topic de otra cuenta, y luego el pull no podria guardarlas. El topic va con LEFT JOIN
    // porque es opcional. NO se exige lastSyncedAt del padre: el deck nuevo sube antes en el mismo
    // push (decks -> topics -> flashcards).
    @Query(
        "SELECT f.* FROM flashcards f " +
            "INNER JOIN decks d ON d.id = f.deckId " +
            "AND d.userId = f.userId " +
            "LEFT JOIN topics t ON t.id = f.topicId " +
            "WHERE f.userId = :userId " +
            "AND (f.topicId IS NULL OR t.userId = f.userId) " +
            "AND (f.lastSyncedAt IS NULL OR f.updatedAt > f.lastSyncedAt)"
    )
    suspend fun getPendingSyncFlashcards(userId: String): List<FlashcardEntity>

    // Sync automatica: ¿hay cambios pendientes de subir? Mismo criterio que getPendingSync* MAS las filas sin
    // dueno (userId NULL = creadas con sesion iniciada y aun sin adoptar; ver countUnowned*). Observable:
    // Room re-emite cuando cambia cualquiera de las tablas de la consulta. Un test de propiedad compara esto
    // con getPendingSync + countUnowned para que no se desvien.
    @Query(
        "SELECT COUNT(*) AS pendingCount, COALESCE(MAX(updatedAt), 0) AS newestUpdatedAt FROM (" +
            "SELECT f.updatedAt AS updatedAt FROM flashcards f " +
            "INNER JOIN decks d ON d.id = f.deckId " +
            "AND d.userId = f.userId " +
            "LEFT JOIN topics t ON t.id = f.topicId " +
            "WHERE f.userId = :userId " +
            "AND (f.topicId IS NULL OR t.userId = f.userId) " +
            "AND (f.lastSyncedAt IS NULL OR f.updatedAt > f.lastSyncedAt) " +
            "UNION ALL SELECT updatedAt FROM flashcards WHERE userId IS NULL)"
    )
    fun observePendingStamp(userId: String): Flow<PendingStampRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFlashcards(flashcards: List<FlashcardEntity>)

    @Query("UPDATE flashcards SET lastSyncedAt = :syncedAt WHERE id IN (:ids)")
    suspend fun markFlashcardsPushed(ids: List<String>, syncedAt: Instant)

    // Bloque F (borrado de cuenta) -- ver comentario en DeckDao.releaseDecksFromUser.
    @Query("UPDATE flashcards SET userId = NULL, lastSyncedAt = NULL WHERE userId = :userId")
    suspend fun releaseFlashcardsFromUser(userId: String): Int

    // Cierre de sesion -- ver comentario en DeckDao.deleteAllDecks.
    @Query("DELETE FROM flashcards")
    suspend fun deleteAllFlashcards()

    @Query("SELECT COUNT(*) FROM flashcards WHERE userId IS NULL")
    suspend fun countUnownedFlashcards(): Int

    // Pull incremental: ver DeckDao.hasAnyDeck (tombstones incluidos).
    @Query("SELECT EXISTS(SELECT 1 FROM flashcards)")
    suspend fun hasAnyFlashcard(): Boolean

    // Guarda del pull: ids de decks / topics que existen localmente (tambien los borrados
    // logicamente). Una flashcard cuyo deck o topic no esta aqui no se puede insertar
    // (foreign_keys = ON).
    @Query("SELECT id FROM decks WHERE id IN (:ids)")
    suspend fun getExistingDeckIds(ids: List<String>): List<String>

    @Query("SELECT id FROM topics WHERE id IN (:ids)")
    suspend fun getExistingTopicIds(ids: List<String>): List<String>

}