package org.braincards.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import org.braincards.app.data.local.entity.FlashcardEntity

/**
 * REGLA UNICA de "tarjeta activa" para las LECTURAS de estudio, recuento y estadisticas (deuda (e)): ademas de
 * `flashcards.deletedAt IS NULL`, su mazo debe existir y estar vivo. Una tarjeta viva bajo un mazo en tombstone (llegada
 * por sync: otro dispositivo la anadio o edito despues del borrado) NO cuenta en Home, ni entra en una sesion de estudio,
 * ni en las estadisticas. Es solo lectura: no escribe ni cambia el protocolo de sync, y si el mazo vuelve a estar vivo
 * (pull con `deletedAt = null`) sus tarjetas reaparecen solas.
 *
 * Se usa con `FROM flashcards` + esta cadena + el WHERE. NO aplicar a `getFlashcardsByDeckId` (`deleteDeck` marca el mazo
 * ANTES de leer sus tarjetas para borrarlas) ni a las consultas de sync (deben ver tombstones y huerfanas para subirlas).
 * `FlashcardQueryRuleGuardTest` comprueba que cada consulta de lectura la usa o esta en su lista de permitidas.
 */
internal const val LIVE_DECK_JOIN = "INNER JOIN decks ON decks.id = flashcards.deckId AND decks.deletedAt IS NULL "

@Dao
interface FlashcardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFlashcard(flashcard: FlashcardEntity)

    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)


    // Tarjeta activa (ver LIVE_DECK_JOIN): la usa la sesion de estudio para saltar tarjetas borradas o de mazos borrados.
    @Query("SELECT flashcards.* FROM flashcards " + LIVE_DECK_JOIN + "WHERE flashcards.id = :id AND flashcards.deletedAt IS NULL")
    suspend fun getFlashcardById(id: String): FlashcardEntity?

    // sync (Bloque E, LWW) -- ver comentario en DeckDao.getDeckByIdIncludingDeleted.
    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getFlashcardByIdIncludingDeleted(id: String): FlashcardEntity?

    // SIN filtro de mazo vivo A PROPOSITO: DeckRepositoryImpl.deleteDeck marca el mazo ANTES de leer sus tarjetas para
    // borrarlas (con el filtro no encontraria ninguna y las dejaria vivas). TopicRepositoryImpl.deleteTopic tambien la usa.
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId AND deletedAt IS NULL")
    fun getFlashcardsByDeckId(deckId: String): Flow<List<FlashcardEntity>>

    // updatedAt tambien se toca al borrar -- ver mismo comentario en DeckDao.softdeleteDeck.
    @Query("UPDATE flashcards SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softdeleteFlashcardById(id: String, deletedAt: Instant)

    // nextReview vive en card_scheduling desde que se separo el scheduling de Flashcard;
    // "due" ahora se resuelve con un LEFT JOIN, seleccionando solo columnas de flashcards.
    // LEFT JOIN + COALESCE: una tarjeta que nunca se ha repasado no tiene fila en
    // card_scheduling (se crea en el primer repaso) y debe contar como vencida. El 0 es
    // epoch en milisegundos, la misma unidad en la que Converters guarda el Instant.
    @Query(
        "SELECT flashcards.* FROM flashcards " +
            "LEFT JOIN card_scheduling ON flashcards.id = card_scheduling.cardId " +
            LIVE_DECK_JOIN +
            "WHERE flashcards.deckId = :deckId AND flashcards.deletedAt IS NULL " +
            "AND COALESCE(card_scheduling.nextReview, 0) <= :now " +
            "ORDER BY COALESCE(card_scheduling.nextReview, 0) ASC"
    )
    suspend fun getDueFlashcardsByDeck(deckId: String, now: Instant): List<FlashcardEntity>

    // Estadisticas: solo tarjetas activas (ver LIVE_DECK_JOIN).
    @Query("SELECT flashcards.* FROM flashcards " + LIVE_DECK_JOIN + "WHERE flashcards.deletedAt IS NULL")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>


    @Query(
        "SELECT flashcards.* FROM flashcards " +
            "LEFT JOIN card_scheduling ON flashcards.id = card_scheduling.cardId " +
            LIVE_DECK_JOIN +
            "WHERE flashcards.deletedAt IS NULL AND COALESCE(card_scheduling.nextReview, 0) <= :now " +
            "ORDER BY COALESCE(card_scheduling.nextReview, 0) ASC"
    )
    suspend fun getAllDueFlashcards(now: Instant): List<FlashcardEntity>

    // Home: tarjetas pendientes de repaso, alcance "todas". DEBE mantener el mismo FROM/WHERE que getAllDueFlashcards
    // (LEFT JOIN + LIVE_DECK_JOIN + deletedAt IS NULL + COALESCE(nextReview, 0) <= :now): es el criterio de la sesion de estudio
    // (StudyScope.All). Un test de propiedad (DueCountRoomTest) compara ambos.
    @Query(
        "SELECT COUNT(*) FROM flashcards " +
            "LEFT JOIN card_scheduling ON flashcards.id = card_scheduling.cardId " +
            LIVE_DECK_JOIN +
            "WHERE flashcards.deletedAt IS NULL AND COALESCE(card_scheduling.nextReview, 0) <= :now"
    )
    fun observeDueCount(now: Instant): Flow<Int>

    // Repaso libre (paso 14): TODAS las tarjetas activas, sin filtrar por pendientes ni mirar card_scheduling. Regla unica de lectura
    // (LIVE_DECK_JOIN): una tarjeta viva bajo un mazo en tombstone NO entra. getFlashcardsByDeckId NO sirve para esto (no aplica la
    // regla). ORDER BY id: orden base estable; el barajado (con semilla) se hace en el caso de uso.
    @Query("SELECT flashcards.* FROM flashcards " + LIVE_DECK_JOIN + "WHERE flashcards.deletedAt IS NULL ORDER BY flashcards.id ASC")
    suspend fun getFreeReviewCards(): List<FlashcardEntity>

    @Query(
        "SELECT flashcards.* FROM flashcards " + LIVE_DECK_JOIN +
            "WHERE flashcards.deckId = :deckId AND flashcards.deletedAt IS NULL ORDER BY flashcards.id ASC"
    )
    suspend fun getFreeReviewCardsByDeck(deckId: String): List<FlashcardEntity>

    // Estadisticas (madurez de las tarjetas), en UNA consulta agregada: sobre las tarjetas ACTIVAS (LIVE_DECK_JOIN) cuenta
    //   - Nuevas      = sin fila en card_scheduling (nunca repasadas);
    //   - Aprendiendo = con fila e interval < 21 dias (incluye la recien fallada: interval 0);
    //   - Maduras     = interval >= 21 dias.
    // "Tarjetas totales" es la SUMA de las tres (se calcula fuera). La consulta parte de `flashcards`, asi que una fila de
    // scheduling de una tarjeta borrada o de un mazo en tombstone no cuenta. cardId es la clave de card_scheduling: el
    // LEFT JOIN no duplica filas. COALESCE: sin tarjetas, SUM da NULL. Una tarjeta con tema borrado bajo un mazo vivo cuenta.
    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN card_scheduling.cardId IS NULL THEN 1 ELSE 0 END), 0) AS newCards, " +
            "COALESCE(SUM(CASE WHEN card_scheduling.cardId IS NOT NULL AND card_scheduling.interval < 21 THEN 1 ELSE 0 END), 0) AS learningCards, " +
            "COALESCE(SUM(CASE WHEN card_scheduling.interval >= 21 THEN 1 ELSE 0 END), 0) AS matureCards " +
            "FROM flashcards " +
            "LEFT JOIN card_scheduling ON card_scheduling.cardId = flashcards.id " +
            LIVE_DECK_JOIN +
            "WHERE flashcards.deletedAt IS NULL"
    )
    fun observeMaturityCounts(): Flow<MaturityCountsRow>


    @Query("UPDATE flashcards SET userId = :userId WHERE userId IS NULL")
    suspend fun claimOrphanedFlashcards(userId: String): Int

    // sync (Bloque D) -- mismo criterio que DeckDao/TopicDao, mas el invariante de dueno: solo se
    // sube una flashcard cuyo deck (y su topic, si tiene) existen localmente (tambien borrados
    // logicamente) y son del MISMO usuario. El INSERT del servidor solo comprueba
    // user_id = auth.uid() y la FK salta RLS: sin esto se subirian flashcards colgadas del
    // deck/topic de otra cuenta, y luego el pull no podria guardarlas. El topic va con LEFT JOIN
    // porque es opcional. NO se exige lastSyncedAt del padre: el deck nuevo sube antes en el mismo
    // push (decks -> topics -> flashcards).
    @Query(
        "SELECT f.* FROM flashcards f " +
            "INNER JOIN decks d ON d.id = f.deckId " +
            "AND d.userId = f.userId " +
            "LEFT JOIN topics t ON t.id = f.topicId " +
            "WHERE f.userId = :userId " +
            "AND (f.topicId IS NULL OR t.userId = f.userId) " +
            "AND (f.lastSyncedAt IS NULL OR f.updatedAt > f.lastSyncedAt)"
    )
    suspend fun getPendingSyncFlashcards(userId: String): List<FlashcardEntity>

    // Sync automatica: ¿hay cambios pendientes de subir? Mismo criterio que getPendingSync* MAS las filas sin
    // dueno (userId NULL = creadas con sesion iniciada y aun sin adoptar; ver countUnowned*). Observable:
    // Room re-emite cuando cambia cualquiera de las tablas de la consulta. Un test de propiedad compara esto
    // con getPendingSync + countUnowned para que no se desvien.
    @Query(
        "SELECT COUNT(*) AS pendingCount, COALESCE(MAX(updatedAt), 0) AS newestUpdatedAt FROM (" +
            "SELECT f.updatedAt AS updatedAt FROM flashcards f " +
            "INNER JOIN decks d ON d.id = f.deckId " +
            "AND d.userId = f.userId " +
            "LEFT JOIN topics t ON t.id = f.topicId " +
            "WHERE f.userId = :userId " +
            "AND (f.topicId IS NULL OR t.userId = f.userId) " +
            "AND (f.lastSyncedAt IS NULL OR f.updatedAt > f.lastSyncedAt) " +
            "UNION ALL SELECT updatedAt FROM flashcards WHERE userId IS NULL)"
    )
    fun observePendingStamp(userId: String): Flow<PendingStampRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFlashcards(flashcards: List<FlashcardEntity>)

    @Query("UPDATE flashcards SET lastSyncedAt = :syncedAt WHERE id IN (:ids)")
    suspend fun markFlashcardsPushed(ids: List<String>, syncedAt: Instant)

    // Bloque F (borrado de cuenta) -- ver comentario en DeckDao.releaseDecksFromUser.
    @Query("UPDATE flashcards SET userId = NULL, lastSyncedAt = NULL WHERE userId = :userId")
    suspend fun releaseFlashcardsFromUser(userId: String): Int

    // Cierre de sesion -- ver comentario en DeckDao.deleteAllDecks.
    @Query("DELETE FROM flashcards")
    suspend fun deleteAllFlashcards()

    @Query("SELECT COUNT(*) FROM flashcards WHERE userId IS NULL")
    suspend fun countUnownedFlashcards(): Int

    // Pull incremental: ver DeckDao.hasAnyDeck (tombstones incluidos).
    @Query("SELECT EXISTS(SELECT 1 FROM flashcards)")
    suspend fun hasAnyFlashcard(): Boolean

    // Guarda del pull: ids de decks / topics que existen localmente (tambien los borrados
    // logicamente). Una flashcard cuyo deck o topic no esta aqui no se puede insertar
    // (foreign_keys = ON).
    @Query("SELECT id FROM decks WHERE id IN (:ids)")
    suspend fun getExistingDeckIds(ids: List<String>): List<String>

    @Query("SELECT id FROM topics WHERE id IN (:ids)")
    suspend fun getExistingTopicIds(ids: List<String>): List<String>

}