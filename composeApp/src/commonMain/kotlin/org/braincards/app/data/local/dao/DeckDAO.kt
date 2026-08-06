package org.braincards.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.braincards.app.data.local.entity.DeckEntity
import kotlinx.datetime.Instant

@Dao
interface DeckDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDeck(deck: DeckEntity)

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Query ("UPDATE decks SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softdeleteDeck(id:String, deletedAt: Instant)

    @Query("SELECT * FROM decks WHERE deletedAt IS NULL")
    fun getAllDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id AND deletedAt IS NULL")
    suspend fun getDeckById(id: String): DeckEntity?
}