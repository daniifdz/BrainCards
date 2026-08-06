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

    @Query ("UPDATE topics SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softdeleteTopic(id:String, deletedAt: Instant)

    @Query("SELECT * FROM topics WHERE deckId = :deckId AND deletedAt IS NULL")
    fun getTopicsByDeckId(deckId: String): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE id = :id AND deletedAt IS NULL")
    suspend fun getTopicById(id: String): TopicEntity?
}