package org.braincards.app.data.local

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Abstraccion minima para ejecutar un bloque (lecturas + escrituras) en UNA transaccion de
 * escritura, sin que los repositorios dependan de Room. En tests se sustituye por un fake
 * que solo ejecuta el bloque.
 */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

/**
 * Produccion: transaccion IMMEDIATE (toma el lock de escritura al empezar), asi que ninguna
 * escritura concurrente (p. ej. el upsert de un repaso) puede colarse entre la lectura local
 * y la escritura del bloque. Las llamadas al DAO dentro del bloque reutilizan esta conexion.
 */
class RoomTransactionRunner(private val database: RoomDatabase) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction { block() }
        }
}
