package org.braincards.app.domain.models

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class Deck @OptIn(ExperimentalTime::class) constructor(
    val id: Int,
    val name: String,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant

)
