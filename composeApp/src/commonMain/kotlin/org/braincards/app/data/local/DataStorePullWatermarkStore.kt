package org.braincards.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.PullWatermarks
import org.braincards.app.domain.models.SyncTable
import org.braincards.app.domain.repository.PullWatermarkStore

/**
 * [PullWatermarkStore] sobre el DataStore de preferencias (braincards.preferences_pb): UNA sola clave
 * ([PULL_WATERMARK_KEY]) con un JSON. Los instantes van como texto ISO en UTC con sufijo Z. Ejemplo:
 *
 *     {"v":1,"userId":"...","fullPullAt":"2026-10-22T10:00:00Z",
 *      "cursors":{"decks":{"ts":"2026-10-22T10:00:00.123456Z","id":"..."}, ...}}
 *
 * Lecturas y escrituras degradan a "sin marca" ante cualquier fallo (JSON corrupto, instante
 * ilegible, version desconocida, error del DataStore) y NUNCA lanzan: la peor consecuencia es un pull
 * completo. No se escribe ningun log: el contenido incluye el id de usuario.
 */
class DataStorePullWatermarkStore(
    private val dataStore: DataStore<Preferences>
) : PullWatermarkStore {

    override suspend fun load(userId: String): PullWatermarks? {
        val raw = try {
            dataStore.data.first()[PULL_WATERMARK_KEY]
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return raw?.let(::decode)?.takeIf { it.userId == userId }?.toDomain()
    }

    override suspend fun saveCursor(userId: String, table: SyncTable, cursor: PullCursor) {
        update(userId) { current ->
            current.copy(
                cursors = current.cursors + (table.storageKey to StoredCursor(cursor.serverUpdatedAt.toString(), cursor.id))
            )
        }
    }

    override suspend fun saveFullPullAt(userId: String, at: Instant) {
        update(userId) { current -> current.copy(fullPullAt = at.toString()) }
    }

    /**
     * Lectura-modificacion-escritura ATOMICA (DataStore serializa los edit): actualizar una tabla
     * parte de lo que hay guardado en ese instante, asi que no pisa el cursor de otra. Si lo guardado
     * es de otro usuario o no se puede leer, se empieza de cero para [userId] (no se reutiliza).
     */
    private suspend fun update(userId: String, change: (Stored) -> Stored) {
        try {
            dataStore.edit { prefs ->
                val current = prefs[PULL_WATERMARK_KEY]?.let(::decode)?.takeIf { it.userId == userId }
                    ?: Stored(userId = userId)
                prefs[PULL_WATERMARK_KEY] = Json.encodeToString(Stored.serializer(), change(current))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Degrada a "sin marca": no se propaga ni se registra nada.
        }
    }

    private fun decode(raw: String): Stored? = try {
        Json.decodeFromString(Stored.serializer(), raw).takeIf { it.v == FORMAT_VERSION }
    } catch (e: Exception) {
        null
    }

    private fun Stored.toDomain(): PullWatermarks {
        val parsed = cursors.mapNotNull { (key, stored) ->
            val table = SyncTable.entries.firstOrNull { it.storageKey == key } ?: return@mapNotNull null
            val ts = try {
                Instant.parse(stored.ts)
            } catch (e: Exception) {
                return@mapNotNull null // cursor ilegible: esa tabla queda sin marca, las demas se conservan
            }
            table to PullCursor(ts, stored.id)
        }.toMap()
        val full = fullPullAt?.let { try { Instant.parse(it) } catch (e: Exception) { null } }
        return PullWatermarks(cursors = parsed, lastFullPullAt = full)
    }

    @Serializable
    private data class StoredCursor(val ts: String, val id: String)

    @Serializable
    private data class Stored(
        val v: Int = FORMAT_VERSION,
        val userId: String,
        val fullPullAt: String? = null,
        val cursors: Map<String, StoredCursor> = emptyMap()
    )

    private companion object {
        const val FORMAT_VERSION = 1
    }
}
