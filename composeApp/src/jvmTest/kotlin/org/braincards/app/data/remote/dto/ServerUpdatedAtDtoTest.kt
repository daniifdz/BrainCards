package org.braincards.app.data.remote.dto

import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.braincards.app.data.mapper.toDto
import org.braincards.app.data.remote.StubHttpEngine
import org.braincards.app.data.remote.deckRow
import org.braincards.app.data.remote.okJson
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `server_updated_at` es la marca de agua de SERVIDOR: solo se lee en el pull y NUNCA se envia en el
 * push; y un servidor que la devuelve no puede romper a un cliente que no la conoce.
 */
class ServerUpdatedAtDtoTest {

    private val t = Instant.parse("2026-10-22T10:00:00Z")

    private val strictJson = Json { encodeDefaults = true }
    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun `los toDto no rellenan la marca de servidor`() {
        assertEquals(null, Deck("d", "n", t, t, null, null, "u", null).toDto().serverUpdatedAt)
        assertEquals(null, Topic("t", "d", "n", t, t, null, null, "u").toDto().serverUpdatedAt)
        assertEquals(
            null,
            Flashcard("f", "d", "q", "a", t, t, CardType.FLIP, null, null, null, "u", null, null).toDto().serverUpdatedAt
        )
        assertEquals(null, CardScheduling("f", 2.5, 1, 1, t, t, "u", null).toDto().serverUpdatedAt)
    }

    @Test
    fun `aunque encodeDefaults sea true el JSON de subida no incluye server_updated_at`() {
        val deck = strictJson.encodeToString(DeckDto.serializer(), Deck("d", "n", t, t, null, null, "u", null).toDto())
        val topic = strictJson.encodeToString(TopicDto.serializer(), Topic("t", "d", "n", t, t, null, null, "u").toDto())
        val card = strictJson.encodeToString(
            FlashcardDto.serializer(),
            Flashcard("f", "d", "q", "a", t, t, CardType.FLIP, null, null, null, "u", null, null).toDto()
        )
        val scheduling = strictJson.encodeToString(
            CardSchedulingDto.serializer(), CardScheduling("f", 2.5, 1, 1, t, t, "u", null).toDto()
        )

        for (json in listOf(deck, topic, card, scheduling)) {
            assertFalse("server_updated_at" in json, "no debe subirse la marca de servidor: $json")
            assertTrue("updated_at" in json, "el updated_at del cliente si se sube: $json")
        }
    }

    @Test
    fun `el pull decodifica server_updated_at con offset y microsegundos`() {
        val dto = lenientJson.decodeFromString(
            DeckDto.serializer(), deckRow("d1", "2026-10-22T10:00:00.123456+00:00")
        )

        assertEquals(Instant.parse("2026-10-22T10:00:00.123456Z"), dto.serverUpdatedAt)
    }

    /** DeckDto sin server_updated_at (forma antigua): lo que decodificaba el cliente antes de incorporar esa columna. */
    @Serializable
    private data class LegacyDeckDto(
        val id: String,
        val name: String,
        val description: String? = null,
        @SerialName("created_at")
        @Serializable(with = InstantIso8601Serializer::class)
        val createdAt: Instant,
        @SerialName("updated_at")
        @Serializable(with = InstantIso8601Serializer::class)
        val updatedAt: Instant,
        @SerialName("deleted_at")
        @Serializable(with = InstantIso8601Serializer::class)
        val deletedAt: Instant? = null,
        @SerialName("last_synced_at")
        @Serializable(with = InstantIso8601Serializer::class)
        val lastSyncedAt: Instant? = null,
        @SerialName("user_id")
        val userId: String
    )

    @Test
    fun `una clave desconocida (server_updated_at y otra futura) no rompe la decodificacion del cliente actual`() = runBlocking {
        // Camino REAL: cliente de supabase-kt con su serializador por defecto (el mismo que usa
        // AppModule, que no lo sobrescribe) y decodeList, no un Json de prueba.
        val body = "[${deckRow("d1", "2026-10-22T10:00:00.123456+00:00").dropLast(1)},\"columna_futura\":{\"x\":1}}]"
        val engine = StubHttpEngine { okJson(body) }
        val client = createSupabaseClient(supabaseUrl = "https://test.supabase.co", supabaseKey = "anon-key") {
            httpEngine = engine
            install(Postgrest)
        }

        val rows = client.from("decks").select().decodeList<LegacyDeckDto>()

        assertEquals(listOf("d1"), rows.map { it.id })
        assertEquals("user-1", rows.single().userId)
    }
}
