package org.braincards.app.domain.models

data class StudyQueue (
    //val para no romper la inmutabilidad
    val queue: List<Flashcard>,
    val correctCount:Int = 0,
    val failedCount:Int = 0
){
    val currentCard: Flashcard? get() = queue.firstOrNull()
    val isFinished: Boolean get() = queue.isEmpty()

    fun submitResult(card: Flashcard, quality: ReviewQuality): StudyQueue{

        //valor nuevo para la cola sin tarjeta actual
        val remainingCards = queue.drop(1)

        return if(quality.value<3){
            copy(
                queue = remainingCards + card,
                failedCount = failedCount + 1
            )
        }else{
            copy(
                queue = remainingCards,
                correctCount = correctCount + 1
            )
        }

    }

}

