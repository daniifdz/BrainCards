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
    suspend fun addFlashcard(flashcard: FlashcardEntity): Long

    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)

    @Delete
    suspend fun deleteFlashcard(flashcard: FlashcardEntity)

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getFlashcardById(id: Int): FlashcardEntity?

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId")
    fun getFlashcardsByDeckId(deckId: Int): Flow<List<FlashcardEntity>>

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteFlashcardById(id: Int)

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId AND nextReview <= :now ORDER BY nextReview ASC")
    suspend fun getDueFlashcardsByDeck(deckId: Int, now: Instant): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>
}