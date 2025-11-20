package org.braincards.app.domain.models

data class Deck(
    val id: String,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt:Long

)
