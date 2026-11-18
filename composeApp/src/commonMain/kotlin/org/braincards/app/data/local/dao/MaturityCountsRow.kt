package org.braincards.app.data.local.dao

/** Resultado de FlashcardDao.observeMaturityCounts: tarjetas activas por madurez. No es una entidad (sin tabla). */
data class MaturityCountsRow(
    val newCards: Int,
    val learningCards: Int,
    val matureCards: Int
)
