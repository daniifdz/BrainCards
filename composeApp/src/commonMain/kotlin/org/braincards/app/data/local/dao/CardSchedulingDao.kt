package org.braincards.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import org.braincards.app.data.local.entity.CardSchedulingEntity

@Dao
interface CardSchedulingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cardScheduling: CardSchedulingEntity)

    @Update
    suspend fun update(cardScheduling: CardSchedulingEntity)

    @Query("SELECT * FROM card_scheduling WHERE cardId = :cardId")
    suspend fun getByCardId(cardId: String): CardSchedulingEntity?

    @Query("SELECT * FROM card_scheduling")
    fun getAll(): Flow<List<CardSchedulingEntity>>

    // --- sync ---

    // Invariante de dueno: scheduling.userId == flashcard.userId (la RLS lo exige). Solo se
    // reclaman filas huerfanas cuya flashcard ya es de este usuario.
    @Query(
        "UPDATE card_scheduling SET userId = :userId " +
            "WHERE userId IS NULL AND cardId IN (SELECT id FROM flashcards WHERE userId = :userId)"
    )
    suspend fun claimOrphanedScheduling(userId: String): Int

    // La flashcard debe estar ya subida/bajada (lastSyncedAt no nulo; incluye tombstones) y
    // ser del mismo dueno; si no, el push daria 409 por FK / violaria la RLS.
    @Query(
        "SELECT cs.* FROM card_scheduling cs " +
            "INNER JOIN flashcards f ON f.id = cs.cardId " +
            "WHERE cs.userId = :userId AND f.userId = cs.userId AND f.lastSyncedAt IS NOT NULL " +
            "AND (cs.lastSyncedAt IS NULL OR cs.updatedAt > cs.lastSyncedAt)"
    )
    suspend fun getPendingSyncScheduling(userId: String): List<CardSchedulingEntity>

    // Sync automatica: ¿hay cambios pendientes de subir? Mismo criterio que getPendingSync* MAS las filas sin
    // dueno (userId NULL = creadas con sesion iniciada y aun sin adoptar; ver countUnowned*). Observable:
    // Room re-emite cuando cambia cualquiera de las tablas de la consulta. Un test de propiedad compara esto
    // con getPendingSync + countUnowned para que no se desvien.
    @Query(
        "SELECT COUNT(*) AS pendingCount, COALESCE(MAX(updatedAt), 0) AS newestUpdatedAt FROM (" +
            "SELECT cs.updatedAt AS updatedAt FROM card_scheduling cs " +
            "INNER JOIN flashcards f ON f.id = cs.cardId " +
            "WHERE cs.userId = :userId AND f.userId = cs.userId AND f.lastSyncedAt IS NOT NULL " +
            "AND (cs.lastSyncedAt IS NULL OR cs.updatedAt > cs.lastSyncedAt) " +
            "UNION ALL SELECT updatedAt FROM card_scheduling WHERE userId IS NULL)"
    )
    fun observePendingStamp(userId: String): Flow<PendingStampRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllScheduling(schedulings: List<CardSchedulingEntity>)

    @Query("UPDATE card_scheduling SET lastSyncedAt = :syncedAt WHERE cardId IN (:ids)")
    suspend fun markSchedulingPushed(ids: List<String>, syncedAt: Instant)

    @Query("UPDATE card_scheduling SET userId = NULL, lastSyncedAt = NULL WHERE userId = :userId")
    suspend fun releaseSchedulingFromUser(userId: String): Int

    // Guard del pull: ids de flashcards que existen localmente (tambien las borradas
    // logicamente). Con foreign_keys = ON un scheduling huerfano aborta todo el lote.
    @Query("SELECT id FROM flashcards WHERE id IN (:ids)")
    suspend fun getExistingFlashcardIds(ids: List<String>): List<String>

    // Cierre de sesion -- ver comentario en DeckDao.deleteAllDecks.
    @Query("DELETE FROM card_scheduling")
    suspend fun deleteAllScheduling()

    @Query("SELECT COUNT(*) FROM card_scheduling WHERE userId IS NULL")
    suspend fun countUnownedScheduling(): Int

    // Pull incremental: ver DeckDao.hasAnyDeck.
    @Query("SELECT EXISTS(SELECT 1 FROM card_scheduling)")
    suspend fun hasAnyScheduling(): Boolean
}
