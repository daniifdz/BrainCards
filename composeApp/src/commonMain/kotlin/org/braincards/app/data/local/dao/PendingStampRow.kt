package org.braincards.app.data.local.dao

/** Resultado de las consultas observePendingStamp de los DAO: cuantas filas pendientes y el updatedAt (ms) mas reciente. */
data class PendingStampRow(
    val pendingCount: Int,
    val newestUpdatedAt: Long
)
