package org.braincards.app.presentation.screens.flashcards

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Topic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Selector de tema del dialogo de tarjeta: nunca se guarda un topicId que ya no existe (FK de Room). */
class ResolveTopicSelectionTest {

    private val t0 = Instant.fromEpochMilliseconds(1_000)
    private fun topic(id: String) = Topic(id, "d1", id, t0, t0)
    private val options = listOf(topic("t1"), topic("t2"))

    // Mutacion R2: si siempre devolviera null, este test fallaria.
    @Test
    fun un_tema_que_sigue_vivo_se_conserva() {
        assertEquals("t2", resolveTopicSelection("t2", options))
    }

    // Mutacion R1: si siempre devolviera el id elegido, este test fallaria.
    @Test
    fun un_tema_que_ya_no_esta_cae_a_Sin_tema() {
        assertNull(resolveTopicSelection("borrado", options))
    }

    @Test
    fun sin_tema_elegido_sigue_siendo_Sin_tema() {
        assertNull(resolveTopicSelection(null, options))
    }

    @Test
    fun sin_ningun_tema_vivo_cualquier_eleccion_cae_a_Sin_tema() {
        assertNull(resolveTopicSelection("t1", emptyList()))
    }
}
