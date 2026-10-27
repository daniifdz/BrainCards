package org.braincards.app.util

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** El helper de logs solo describe la clase de la excepcion (y el codigo HTTP): nunca message ni cabeceras. */
@OptIn(InternalAPI::class)
class SafeLogTest {

    // Valores centinela: si aparecen en la salida, el helper esta volcando message/cabeceras.
    private val secretKey = "CENTINELA-TOKEN-ABC123"
    private val serverMessage = "mensaje-del-servidor-XYZ"

    private class StatusEngine(private val status: HttpStatusCode, private val body: String) :
        HttpClientEngineBase("safe-log-test-engine") {
        override val config = HttpClientEngineConfig()
        override val supportedCapabilities = setOf<HttpClientEngineCapability<*>>(HttpTimeoutCapability)

        override suspend fun execute(data: HttpRequestData): HttpResponseData = HttpResponseData(
            status, GMTDate(), headersOf(HttpHeaders.ContentType, "application/json"),
            HttpProtocolVersion.HTTP_1_1, ByteReadChannel(body.toByteArray()), callContext()
        )
    }

    /** RestException REAL (401) cuyo message lleva URL y cabeceras de la peticion, con Authorization. */
    private fun realRestException(): RestException = runBlocking {
        val client = createSupabaseClient(supabaseUrl = "https://test.supabase.co", supabaseKey = secretKey) {
            httpEngine = StatusEngine(
                HttpStatusCode.Unauthorized,
                """{"message":"$serverMessage","code":"PGRST301","details":null,"hint":null}"""
            )
            install(Postgrest)
        }
        val failure = runCatching { client.from("decks").select() }.exceptionOrNull()
        assertIs<RestException>(failure)
    }

    @Test
    fun `la excepcion de prueba lleva de verdad la cabecera Authorization en su message`() {
        val e = realRestException()

        assertTrue(e.message!!.contains(secretKey), "el escenario debe tener el secreto en el message")
        assertTrue(e.message!!.contains(serverMessage))
    }

    @Test
    fun `una RestException se describe con su clase y su codigo HTTP, sin message ni cabeceras`() {
        val e = realRestException()

        val described = e.safeLogDescription()

        assertTrue(described.endsWith("(HTTP 401)"), described)
        assertFalse(described.contains(secretKey), "no debe llevar el valor de la cabecera")
        assertFalse(described.contains(serverMessage), "no debe llevar el message")
        assertFalse(described.contains("Authorization"))
        assertFalse(described.contains("Headers"))
        assertFalse(described.contains("test.supabase.co"), "no debe llevar la URL")
    }

    @Test
    fun `una excepcion cualquiera se describe solo con el nombre de su clase`() {
        val e = IllegalStateException("correo=a@b.com token=$secretKey")

        assertEquals("IllegalStateException", e.safeLogDescription())
    }

    @Test
    fun `logError imprime contexto y descripcion segura, sin el message`() {
        val e = realRestException()
        val captured = java.io.ByteArrayOutputStream()
        val original = System.out
        System.setOut(java.io.PrintStream(captured, true, "UTF-8"))
        try {
            logError("Error al iniciar sesión", e)
        } finally {
            System.setOut(original)
        }

        val line = captured.toString("UTF-8").trim()

        assertTrue(line.startsWith("Error al iniciar sesión: "), line)
        assertTrue(line.contains("(HTTP 401)"), line)
        assertFalse(line.contains(secretKey), line)
        assertFalse(line.contains(serverMessage), line)
    }
}
