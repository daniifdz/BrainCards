package org.braincards.app.domain.repository

/**
 * Operaciones sobre la base de datos local COMPLETA, no sobre una entidad concreta. Existe
 * aparte de DecksRepository & co. a proposito: esas interfaces no se tocan (regla de oro de
 * CLAUDE.md) y esto no es CRUD de un tipo, sino la limpieza al cerrar sesion (aislamiento
 * de datos entre cuentas en un mismo dispositivo).
 */
interface LocalDataRepository {

    /**
     * Borra FISICAMENTE todas las filas de todas las tablas de usuario (decks, topics,
     * flashcards, card_scheduling, reviewlog), tombstones incluidos, sea cual sea su dueno
     * (cualquier cuenta o ninguna). Todo o nada: una sola transaccion, asi que si algo falla
     * no se borra nada. No se propaga a Supabase (no es un borrado logico).
     */
    suspend fun wipeAll()

    /**
     * Filas SIN dueno (userId NULL) en las cuatro tablas sincronizables. Con sesion iniciada,
     * son filas creadas desde el ultimo sync que el backfill todavia no ha adoptado: no las
     * ve getPendingSync(userId), pero se perderian igual al borrar. reviewlog no cuenta
     * (no se sincroniza).
     */
    suspend fun countUnownedRows(): Int
}
