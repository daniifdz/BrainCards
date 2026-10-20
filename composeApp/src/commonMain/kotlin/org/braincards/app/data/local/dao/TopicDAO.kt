package org.braincards.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import org.braincards.app.data.local.entity.TopicEntity

@Dao
interface TopicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTopic(topic: TopicEntity)

    @Update
    suspend fun updateTopic(topic: TopicEntity)

    // updatedAt tambien se toca al borrar -- ver mismo comentario en DeckDao.softdeleteDeck.
    @Query("UPDATE topics SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softdeleteTopic(id: String, deletedAt: Instant)

    @Query("SELECT * FROM topics WHERE deckId = :deckId AND deletedAt IS NULL")
    fun getTopicsByDeckId(deckId: String): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE id = :id AND deletedAt IS NULL")
    suspend fun getTopicById(id: String): TopicEntity?

    // sync (Bloque E, LWW) -- ver comentario en DeckDao.getDeckByIdIncludingDeleted.
    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun getTopicByIdIncludingDeleted(id: String): TopicEntity?

    @Query("UPDATE topics SET userId = :userId WHERE userId IS NULL")
    suspend fun claimOrphanedTopics(userId: String): Int

    // sync (Bloque D) -- mismo criterio que DeckDao.
    @Query("SELECT * FROM topics WHERE userId = :userId AND (lastSyncedAt IS NULL OR updatedAt > lastSyncedAt)")
    suspend fun getPendingSyncTopics(userId: String): List<TopicEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTopics(topics: List<TopicEntity>)

    @Query("UPDATE topics SET lastSyncedAt = :syncedAt WHERE id IN (:ids)")
    suspend fun markTopicsPushed(ids: List<String>, syncedAt: Instant)

    // Bloque F (borrado de cuenta) -- ver comentario en DeckDao.releaseDecksFromUser.
    @Query("UPDATE topics SET userId = NULL, lastSyncedAt = NULL WHERE userId = :userId")
    suspend fun releaseTopicsFromUser(userId: String): Int

    // Cierre de sesion -- ver comentario en DeckDao.deleteAllDecks.
    @Query("DELETE FROM topics")
    suspend fun deleteAllTopics()

    @Query("SELECT COUNT(*) FROM topics WHERE userId IS NULL")
    suspend fun countUnownedTopics(): Int

    // Guarda del pull: ids de decks que existen localmente (tambien los borrados logicamente).
    // Un topic cuyo deck no esta aqui no se puede insertar (foreign_keys = ON).
    @Query("SELECT id FROM decks WHERE id IN (:ids)")
    suspend fun getExistingDeckIds(ids: List<String>): List<String>
}