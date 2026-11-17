package org.braincards.app.domain.usecase.Flashcards

/** Los dos campos de texto obligatorios de una tarjeta flip. */
enum class CardField { Question, Answer }

/**
 * Anverso y/o reverso en blanco. Hereda de IllegalArgumentException para no romper a quien ya capturaba la excepcion
 * generica; la UI mira [fields] (nunca el mensaje) para poner el error en el campo que toca. Con los dos vacios
 * lleva los dos.
 */
class FlashcardFieldException(val fields: Set<CardField>) : IllegalArgumentException(fields.joinToString { it.name })

/**
 * Valida anverso y reverso: en blanco (vacio o solo espacios) = error de ESE campo. El contenido NO se recorta (una
 * pregunta o respuesta puede ser multilinea).
 */
fun checkFlashcardFields(question: String, answer: String) {
    val blank = buildSet {
        if (question.isBlank()) add(CardField.Question)
        if (answer.isBlank()) add(CardField.Answer)
    }
    if (blank.isNotEmpty()) throw FlashcardFieldException(blank)
}
