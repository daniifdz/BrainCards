package org.braincards.app.data.local.dao

import androidx.room.Dao
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

    // Sync (LWW): ver DeckDao.getDeckByIdIncludingDeleted.
    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun getTopicByIdIncludingDeleted(id: String): TopicEntity?

    @Query("UPDATE topics SET userId = :userId WHERE userId IS NULL")
    suspend fun claimOrphanedTopics(userId: String): Int

    // Sync: mismo criterio que DeckDao, más el invariante de dueño: solo se sube un tema cuyo mazo existe localmente (también
    // borrado lógicamente) y es del mismo usuario (el INSERT del servidor solo comprueba user_id = auth.uid(), no de quién es el
    // mazo, y la FK salta RLS: sin esto se subirían temas colgados del mazo de otra cuenta). No se exige d.lastSyncedAt: el mazo
    // nuevo sube antes en el mismo push (mazos -> temas -> tarjetas).
    @Query(
        "SELECT t.* FROM topics t " +
            "INNER JOIN decks d ON d.id = t.deckId " +
            "AND d.userId = t.userId " +
            "WHERE t.userId = :userId " +
            "AND (t.lastSyncedAt IS NULL OR t.updatedAt > t.lastSyncedAt)"
    )
    suspend fun getPendingSyncTopics(userId: String): List<TopicEntity>

    // Sync automatica: ¿hay cambios pendientes de subir? Mismo criterio que getPendingSync* MAS las filas sin
    // dueno (userId NULL = creadas con sesion iniciada y aun sin adoptar; ver countUnowned*). Observable:
    // Room re-emite cuando cambia cualquiera de las tablas de la consulta. Un test de propiedad compara esto
    // con getPendingSync + countUnowned para que no se desvien.
    @Query(
        "SELECT COUNT(*) AS pendingCount, COALESCE(MAX(updatedAt), 0) AS newestUpdatedAt FROM (" +
            "SELECT t.updatedAt AS updatedAt FROM topics t " +
            "INNER JOIN decks d ON d.id = t.deckId " +
            "AND d.userId = t.userId " +
            "WHERE t.userId = :userId " +
            "AND (t.lastSyncedAt IS NULL OR t.updatedAt > t.lastSyncedAt) " +
            "UNION ALL SELECT updatedAt FROM topics WHERE userId IS NULL)"
    )
    fun observePendingStamp(userId: String): Flow<PendingStampRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTopics(topics: List<TopicEntity>)

    @Query("UPDATE topics SET lastSyncedAt = :syncedAt WHERE id IN (:ids)")
    suspend fun markTopicsPushed(ids: List<String>, syncedAt: Instant)

    // Borrado de cuenta: ver DeckDao.releaseDecksFromUser.
    @Query("UPDATE topics SET userId = NULL, lastSyncedAt = NULL WHERE userId = :userId")
    suspend fun releaseTopicsFromUser(userId: String): Int

    // Cierre de sesion -- ver comentario en DeckDao.deleteAllDecks.
    @Query("DELETE FROM topics")
    suspend fun deleteAllTopics()

    @Query("SELECT COUNT(*) FROM topics WHERE userId IS NULL")
    suspend fun countUnownedTopics(): Int

    // Pull incremental: ver DeckDao.hasAnyDeck (tombstones incluidos).
    @Query("SELECT EXISTS(SELECT 1 FROM topics)")
    suspend fun hasAnyTopic(): Boolean

    // Guarda del pull: ids de decks que existen localmente (tambien los borrados logicamente).
    // Un topic cuyo deck no esta aqui no se puede insertar (foreign_keys = ON).
    @Query("SELECT id FROM decks WHERE id IN (:ids)")
    suspend fun getExistingDeckIds(ids: List<String>): List<String>
}