package org.braincards.app.presentation.screens.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class EntryPendingTest {
    @Test
    fun tabla() {
        // (ruta, tema leido, tiempo agotado) -> pendiente
        val cases = listOf(
            Triple(EntryRoute.Pending, true, false) to true,   // auth sin resolver manda siempre
            Triple(EntryRoute.Pending, false, true) to true,
            Triple(EntryRoute.Home, false, false) to true,     // tema sin leer: espera
            Triple(EntryRoute.Home, false, true) to false,     // tope de 1 s agotado: arranca
            Triple(EntryRoute.Home, true, false) to false,
            Triple(EntryRoute.Welcome, true, true) to false
        )
        for ((input, expected) in cases) {
            assertEquals(expected, shouldShowEntryPending(input.first, input.second, input.third), input.toString())
        }
    }

    @Test
    fun el_tope_es_1_segundo() = assertEquals(1_000L, THEME_LOAD_TIMEOUT_MS)
}
