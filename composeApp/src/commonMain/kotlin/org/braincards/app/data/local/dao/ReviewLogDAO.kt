package org.braincards.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.braincards.app.data.local.entity.ReviewLogEntity

@Dao
interface ReviewLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addReviewLog(reviewLog: ReviewLogEntity)

    // Cierre de sesion -- ver comentario en DeckDao.deleteAllDecks. reviewlog no se
    // sincroniza, asi que lo que se borra aqui no vuelve.
    @Query("DELETE FROM reviewlog")
    suspend fun deleteAllReviewLogs()

}