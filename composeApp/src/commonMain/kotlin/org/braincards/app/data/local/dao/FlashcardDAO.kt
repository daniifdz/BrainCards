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

    @Query("UPDATE flashcards SET userId = :userId WHERE userId IS NULL")
    suspend fun claimOrphanedFlashcards(userId: String): Int

    // sync (Bloque D) -- mismo criterio que DeckDao/TopicDao.
    @Query("SELECT * FROM flashcards WHERE userId = :userId AND (lastSyncedAt IS NULL OR updatedAt > lastSyncedAt)")
    suspend fun getPendingSyncFlashcards(userId: String): List<FlashcardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFlashcards(flashcards: List<FlashcardEntity>)

    @Query("UPDATE flashcards SET lastSyncedAt = :syncedAt WHERE id IN (:ids)")
    suspend fun markFlashcardsPushed(ids: List<String>, syncedAt: Instant)

    // Bloque F (borrado de cuenta) -- ver comentario en DeckDao.releaseDecksFromUser.
    @Query("UPDATE flashcards SET userId = NULL, lastSyncedAt = NULL WHERE userId = :userId")
    suspend fun releaseFlashcardsFromUser(userId: String): Int

}