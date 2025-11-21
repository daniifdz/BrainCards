package org.braincards.app.domain.models

import kotlinx.datetime.Instant
data class Deck (
    val id: Int,
    val name: String,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant

)
