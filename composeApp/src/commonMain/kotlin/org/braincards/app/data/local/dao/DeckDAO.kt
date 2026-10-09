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

    // updatedAt tambien se toca al borrar -- si no, el PUSH del sync (que detecta
    // pendientes comparando updatedAt vs lastSyncedAt) nunca veria un borrado logico
    // sin otro cambio de contenido como "pendiente de subir".
    @Query("UPDATE decks SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softdeleteDeck(id: String, deletedAt: Instant)


    @Query("SELECT * FROM decks WHERE deletedAt IS NULL")
    fun getAllDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id AND deletedAt IS NULL")
    suspend fun getDeckById(id: String): DeckEntity?

    // sync (Bloque E, LWW) -- sin filtro deletedAt IS NULL a proposito: para comparar
    // updatedAt contra una fila remota del pull, un tombstone local sigue siendo la
    // fila real (con su updatedAt real), no "no existe". Si usaramos getDeckById aqui,
    // una fila borrada localmente parecería inexistente y el pull la resucitaria.
    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckByIdIncludingDeleted(id: String): DeckEntity?

    @Query("UPDATE decks SET userId = :userId WHERE userId IS NULL")
    suspend fun claimOrphanedDecks(userId: String): Int

    // sync (Bloque D) -- sin filtro deletedAt IS NULL a proposito, ver comentario
    // en DecksRepository.getPendingSync.
    @Query("SELECT * FROM decks WHERE userId = :userId AND (lastSyncedAt IS NULL OR updatedAt > lastSyncedAt)")
    suspend fun getPendingSyncDecks(userId: String): List<DeckEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDecks(decks: List<DeckEntity>)

    @Query("UPDATE decks SET lastSyncedAt = :syncedAt WHERE id IN (:ids)")
    suspend fun markDecksPushed(ids: List<String>, syncedAt: Instant)

    // Bloque F (borrado de cuenta) -- inverso de claimOrphanedDecks. Sin filtro
    // deletedAt IS NULL a proposito: un tombstone tambien debe liberarse.
    @Query("UPDATE decks SET userId = NULL, lastSyncedAt = NULL WHERE userId = :userId")
    suspend fun releaseDecksFromUser(userId: String): Int
}