package org.braincards.app.presentation.screens.studyStats

import org.braincards.app.domain.models.StudyStats

/** Lo que muestra Estadisticas (handoff 5.7). Loading y Error dependen de la carga; Empty y Content los decide [selectStatsState]. */
sealed interface StatsUiState {
    /** Esqueleto: aun no han llegado los tres datos. */
    data object Loading : StatsUiState

    /** Fallo al leer (la UI ofrece Reintentar). */
    data object Error : StatsUiState

    /** 0 mazos vivos: «Aun no hay nada que mostrar» + «Crear tu primer mazo» (mismo patron que el primer uso de Home). */
    data object Empty : StatsUiState

    /**
     * Hay al menos un mazo. Con mazos pero SIN tarjetas las tres casillas muestran 0/0/0 y el donut no se dibuja
     * ([hasCards] = false): no hace falta un estado propio (handoff v4.14).
     */
    data class Content(val deckCount: Int, val stats: StudyStats, val dueCount: Int) : StatsUiState {
        val hasCards: Boolean get() = stats.totalCards > 0

        /** «Tarjetas totales» = suma de las tres categorias del donut. */
        val totalCards: Int get() = stats.totalCards
    }
}

/**
 * Seleccion pura del estado (sin Loading ni Error). Prioridad: 0 mazos -> [StatsUiState.Empty]; si no,
 * [StatsUiState.Content]. El numero de mazos y la madurez salen de dos consultas distintas: entre dos emisiones puede
 * haber un instante desfasado, y es inofensivo porque manda «0 mazos» y el total sale de la misma fila que el donut.
 */
fun selectStatsState(deckCount: Int, stats: StudyStats, dueCount: Int): StatsUiState =
    if (deckCount == 0) StatsUiState.Empty else StatsUiState.Content(deckCount, stats, dueCount)

/**
 * Porcentajes enteros de [counts] por el METODO DEL MAYOR RESTO: se reparte el suelo de cada porcentaje y los puntos que
 * faltan hasta 100 van a las categorias con mayor resto (a igualdad, la primera). Siempre suman 100 (0 si no hay nada
 * que repartir), a diferencia del redondeo simple (73/64/47 daria 40/35/26 = 101). Aritmetica entera: sin coma flotante.
 */
fun maturityPercentages(counts: List<Int>): List<Int> {
    val total = counts.sum()
    if (total <= 0) return counts.map { 0 }
    val floors = counts.map { it * 100 / total }.toMutableList()
    val remainders = counts.map { it * 100 % total }
    var missing = 100 - floors.sum()
    val order = counts.indices.sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
    for (index in order) {
        if (missing <= 0) break
        floors[index] += 1
        missing--
    }
    return floors
}

/** Porcentajes de la madurez en el orden del donut: Nuevas, Aprendiendo, Maduras. */
fun StudyStats.percentages(): List<Int> = maturityPercentages(listOf(newCards, learningCards, matureCards))
