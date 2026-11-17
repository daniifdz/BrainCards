package org.braincards.app.domain.usecase.Topics

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.Topic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddTopicUseCaseTest {

    private val repository = FakeTopicsRepository()
    private val useCase = AddTopicUseCase(repository)

    private fun topic(id: String, name: String, deckId: String = "deck-1") = Topic(
        id = id, deckId = deckId, name = name,
        createdAt = Clock.System.now(), updatedAt = Clock.System.now()
    )

    @Test
    fun `when topic name is blank should throw IllegalArgumentException`() = runBlocking {
        val invalidTopic = Topic(
            id = "topic-1",
            deckId = "deck-1",
            name = "",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )

        assertFailsWith<IllegalArgumentException> {
            useCase(invalidTopic)
        }
        Unit
    }

    @Test
    fun `when topic is valid should add topic to repository`() = runBlocking {
        val topic = Topic(
            id = "topic-1",
            deckId = "deck-1",
            name = "Verbs",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )

        useCase(topic)

        assertTrue(repository.addedTopics.contains(topic))
    }

    @Test
    fun un_nombre_duplicado_en_el_mazo_es_Duplicate_y_no_se_anade() = runBlocking<Unit> {
        useCase(topic("a", "Verbs"))

        val e = assertFailsWith<TopicNameException> { useCase(topic("b", "  verbs ")) }

        assertEquals(TopicNameError.Duplicate, e.reason)
        assertEquals(listOf("a"), repository.addedTopics.map { it.id })
    }

    @Test
    fun el_mismo_nombre_en_otro_mazo_se_anade() = runBlocking<Unit> {
        useCase(topic("a", "Verbs", deckId = "deck-1"))
        useCase(topic("b", "Verbs", deckId = "deck-2"))
        assertEquals(listOf("a", "b"), repository.addedTopics.map { it.id })
    }

    @Test
    fun guarda_el_nombre_recortado_y_sin_userId_como_los_mazos() = runBlocking<Unit> {
        useCase(topic("a", "  Verbs  "))
        val saved = repository.addedTopics.single()
        assertEquals("Verbs", saved.name)
        assertNull(saved.userId) // lo adopta despues el backfill, igual que un mazo nuevo (AddDeckUseCase)
    }
}
