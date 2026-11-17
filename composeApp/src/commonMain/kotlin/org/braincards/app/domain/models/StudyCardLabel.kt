package org.braincards.app.domain.models

/** Etiqueta "Mazo · Tema" de la tarjeta que se estudia ([topicName] = null si no tiene tema). */
data class StudyCardLabel(val deckName: String, val topicName: String?)
