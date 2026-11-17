package org.braincards.app.domain.usecase.Topics

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.Topic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateTopicUseCaseTest {

    private val repository = FakeTopicsRepository()
    private val useCase = UpdateTopicUseCase(repository)
    private val t0 = Instant.fromEpochMilliseconds(1_000)

    private fun topic(id: String, name: String, deckId: String = "d1") =
        Topic(id = id, deckId = deckId, name = name, createdAt = t0, updatedAt = t0)

    private suspend fun seed(vararg topics: Topic) = topics.forEach { repository.addTopic(it) }

    @Test
    fun un_nombre_en_blanco_se_rechaza_como_Blank() = runBlocking<Unit> {
        seed(topic("a", "Verbs"))
        val e = assertFailsWith<TopicNameException> { useCase(topic("a", "  ")) }
        assertEquals(TopicNameError.Blank, e.reason)
    }

    @Test
    fun renombrar_a_un_nombre_de_otro_tema_del_mazo_es_Duplicate_y_no_guarda() = runBlocking<Unit> {
        seed(topic("a", "Verbs"), topic("b", "Nouns"))
        val e = assertFailsWith<TopicNameException> { useCase(topic("a", " nouns ")) }
        assertEquals(TopicNameError.Duplicate, e.reason)
        assertEquals("Verbs", repository.getTopicById("a")?.name)
    }

    @Test
    fun renombrar_cambiando_solo_mayusculas_del_propio_nombre_es_valido() = runBlocking<Unit> {
        seed(topic("a", "phrasal"))
        useCase(topic("a", "Phrasal"))
        assertEquals("Phrasal", repository.getTopicById("a")?.name)
    }

    // M6: sin la rama "unchanged" (tolerar duplicados ya existentes) este test fallaria.
    @Test
    fun guardar_sin_cambiar_el_nombre_tolera_un_duplicado_que_ya_existia() = runBlocking<Unit> {
        seed(topic("a", "Verbs"), topic("b", "verbs"))
        useCase(topic("a", "Verbs"))
        assertEquals("Verbs", repository.getTopicById("a")?.name)
    }

    @Test
    fun el_mismo_nombre_en_otro_mazo_no_choca() = runBlocking<Unit> {
        seed(topic("a", "Verbs"), topic("b", "Nouns", deckId = "d2"))
        useCase(topic("a", "Nouns"))
        assertEquals("Nouns", repository.getTopicById("a")?.name)
    }

    @Test
    fun guarda_el_nombre_recortado() = runBlocking<Unit> {
        seed(topic("a", "Verbs"))
        useCase(topic("a", "  Adjectives "))
        assertEquals("Adjectives", repository.getTopicById("a")?.name)
    }
}
