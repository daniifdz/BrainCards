package org.braincards.app.domain.usecase.Topics

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.Topic
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AddTopicUseCaseTest {

    private val repository = FakeTopicsRepository()
    private val useCase = AddTopicUseCase(repository)

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
}
