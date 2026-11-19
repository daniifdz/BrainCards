package org.braincards.app.data.local.dao

import androidx.room.Dao
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

    // updatedAt tambien se toca al borrar -- si no, el PUSH del sync (que detecta
    // pendientes comparando updatedAt vs lastSyncedAt) nunca veria un borrado logico
    // sin otro cambio de contenido como "pendiente de subir".
    @Query("UPDATE decks SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softdeleteDeck(id: String, deletedAt: Instant)


    @Query("SELECT * FROM decks WHERE deletedAt IS NULL")
    fun getAllDecks(): Flow<List<DeckEntity>>

    // Lista de mazos: cada mazo vivo con su recuento de tarjetas vivas en UNA consulta agregada (no N). El filtro
    // deletedAt de las tarjetas va en el ON (no en el WHERE) para que un mazo sin tarjetas vivas siga saliendo con 0.
    // Room re-emite cuando cambia decks o flashcards. Sin ORDER BY: el orden (con plegado de acentos) es del dominio.
    @Query(
        "SELECT d.*, COUNT(f.id) AS cardCount FROM decks d " +
            "LEFT JOIN flashcards f ON f.deckId = d.id AND f.deletedAt IS NULL " +
            "WHERE d.deletedAt IS NULL GROUP BY d.id"
    )
    fun observeDeckSummaries(): Flow<List<DeckSummaryRow>>

    @Query("SELECT * FROM decks WHERE id = :id AND deletedAt IS NULL")
    suspend fun getDeckById(id: String): DeckEntity?

    // Sync (LWW): sin filtro deletedAt IS NULL a propósito. Para comparar updatedAt con una fila remota del pull, un tombstone
    // local sigue siendo la fila real (con su updatedAt real), no «no existe». Con getDeckById, una fila borrada localmente
    // parecería inexistente y el pull la resucitaría.
    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckByIdIncludingDeleted(id: String): DeckEntity?

    @Query("UPDATE decks SET userId = :userId WHERE userId IS NULL")
    suspend fun claimOrphanedDecks(userId: String): Int

    // Sync: sin filtro deletedAt IS NULL a propósito, ver DecksRepository.getPendingSync.
    @Query("SELECT * FROM decks WHERE userId = :userId AND (lastSyncedAt IS NULL OR updatedAt > lastSyncedAt)")
    suspend fun getPendingSyncDecks(userId: String): List<DeckEntity>

    // Sync automatica: ¿hay cambios pendientes de subir? Mismo criterio que getPendingSync* MAS las filas sin
    // dueno (userId NULL = creadas con sesion iniciada y aun sin adoptar; ver countUnowned*). Observable:
    // Room re-emite cuando cambia cualquiera de las tablas de la consulta. Un test de propiedad compara esto
    // con getPendingSync + countUnowned para que no se desvien.
    @Query(
        "SELECT COUNT(*) AS pendingCount, COALESCE(MAX(updatedAt), 0) AS newestUpdatedAt FROM (" +
            "SELECT updatedAt FROM decks WHERE userId = :userId AND (lastSyncedAt IS NULL OR updatedAt > lastSyncedAt) " +
            "UNION ALL SELECT updatedAt FROM decks WHERE userId IS NULL)"
    )
    fun observePendingStamp(userId: String): Flow<PendingStampRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDecks(decks: List<DeckEntity>)

    @Query("UPDATE decks SET lastSyncedAt = :syncedAt WHERE id IN (:ids)")
    suspend fun markDecksPushed(ids: List<String>, syncedAt: Instant)

    // Inverso de claimOrphanedDecks (borrado de cuenta). Sin filtro deletedAt IS NULL a propósito: un tombstone también debe
    // liberarse.
    @Query("UPDATE decks SET userId = NULL, lastSyncedAt = NULL WHERE userId = :userId")
    suspend fun releaseDecksFromUser(userId: String): Int

    // Cierre de sesion (aislamiento entre cuentas) -- ver LocalDataRepository. Sin filtro
    // deletedAt a proposito: el borrado es fisico y total, tombstones incluidos.
    @Query("DELETE FROM decks")
    suspend fun deleteAllDecks()

    @Query("SELECT COUNT(*) FROM decks WHERE userId IS NULL")
    suspend fun countUnownedDecks(): Int

    // Pull incremental: ¿la tabla tiene alguna fila? Cuenta TODAS, tombstones incluidos (sin filtro
    // deletedAt a proposito): una tabla con solo borrados logicos no esta vacia.
    @Query("SELECT EXISTS(SELECT 1 FROM decks)")
    suspend fun hasAnyDeck(): Boolean
}