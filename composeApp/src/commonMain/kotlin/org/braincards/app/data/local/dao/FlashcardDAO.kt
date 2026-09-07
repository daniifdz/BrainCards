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

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId AND deletedAt IS NULL")
    fun getFlashcardsByDeckId(deckId: String): Flow<List<FlashcardEntity>>

    @Query("UPDATE flashcards SET deletedAt  = :deletedAt WHERE id = :id")
    suspend fun softdeleteFlashcardById(id: String, deletedAt: Instant)

    // nextReview vive en card_scheduling desde que se separo el scheduling de Flashcard;
    // "due" ahora se resuelve con un JOIN, seleccionando solo columnas de flashcards.
    @Query(
        "SELECT flashcards.* FROM flashcards " +
            "JOIN card_scheduling ON flashcards.id = card_scheduling.cardId " +
            "WHERE flashcards.deckId = :deckId AND flashcards.deletedAt IS NULL " +
            "AND card_scheduling.nextReview <= :now " +
            "ORDER BY card_scheduling.nextReview ASC"
    )
    suspend fun getDueFlashcardsByDeck(deckId: String, now: Instant): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE deletedAt IS NULL")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>


    @Query(
        "SELECT flashcards.* FROM flashcards " +
            "JOIN card_scheduling ON flashcards.id = card_scheduling.cardId " +
            "WHERE flashcards.deletedAt IS NULL AND card_scheduling.nextReview <= :now " +
            "ORDER BY card_scheduling.nextReview ASC"
    )
    suspend fun getAllDueFlashcards(now: Instant): List<FlashcardEntity>

    @Query("UPDATE flashcards SET userId = :userId WHERE userId IS NULL")
    suspend fun claimOrphanedFlashcards(userId: String): Int

}