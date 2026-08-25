package org.braincards.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.braincards.app.data.local.entity.CardSchedulingEntity

@Dao
interface CardSchedulingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cardScheduling: CardSchedulingEntity)

    @Update
    suspend fun update(cardScheduling: CardSchedulingEntity)

    @Query("SELECT * FROM card_scheduling WHERE cardId = :cardId")
    suspend fun getByCardId(cardId: String): CardSchedulingEntity?
}
