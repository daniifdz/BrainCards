package org.braincards.app.data.local.dao

import androidx.room.Embedded
import org.braincards.app.data.local.entity.DeckEntity

/** Resultado de DeckDao.observeDeckSummaries: el mazo y cuantas tarjetas vivas tiene. No es una entidad (sin tabla). */
data class DeckSummaryRow(
    @Embedded val deck: DeckEntity,
    val cardCount: Int
)
