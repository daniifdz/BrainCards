package org.braincards.app.domain.models

import kotlinx.datetime.Instant
data class Deck (
    val id: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val description: String?,


    //nulables porque un mazo recien creado sin synct activado, no tiene
        //valor todavia "deleteAt" no estaria borrado
    val deletedAt: Instant? = null,
    val userId: String? = null,
    val lastSyncedAt: Instant? = null

)
