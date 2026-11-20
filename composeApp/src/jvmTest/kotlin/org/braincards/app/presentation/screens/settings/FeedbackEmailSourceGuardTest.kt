package org.braincards.app.presentation.screens.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guarda de código fuente: la dirección de «Enviar comentarios» vive en UN solo sitio (`FEEDBACK_EMAIL`, en `FeedbackDialog.kt`) y
 * ninguna otra dirección de correo se escribe a mano en el código de producción. Lee los ficheros e ignora los comentarios.
 */
class FeedbackEmailSourceGuardTest {

    private val srcDir: File = run {
        val relative = "composeApp/src"
        val dir = generateSequence(File("").absoluteFile) { it.parentFile }
            .take(6)
            .map { File(it, relative) }
            .firstOrNull { it.isDirectory }
        assertNotNull(dir, "no se encuentra $relative subiendo desde ${File("").absolutePath}")
        dir
    }

    private fun stripComments(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\n]*"), "")

    private fun productionSources(): List<File> =
        listOf("commonMain", "androidMain", "jvmMain").flatMap { set ->
            File(srcDir, set).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")

    @Test
    fun la_direccion_aparece_una_sola_vez_en_el_codigo_de_produccion() {
        val hits = productionSources().flatMap { f -> email.findAll(stripComments(f.readText())).map { f.name to it.value }.toList() }
        assertEquals(1, hits.size, "direcciones escritas a mano: $hits")
        assertEquals("FeedbackDialog.kt", hits.single().first, "la única aparición debe ser FEEDBACK_EMAIL")
    }

    @Test
    fun enviar_comentarios_usa_la_constante_y_avisa_cuando_no_se_abre() {
        val settings = stripComments(
            File(srcDir, "commonMain/kotlin/org/braincards/app/presentation/screens/settings/SettingsScreen.kt").readText()
        )
        assertTrue("recipient = FEEDBACK_EMAIL" in settings, "el destinatario sale de FEEDBACK_EMAIL")
        assertTrue("if (!opened) showFeedbackUnavailableDialog = true" in settings, "si no se abre el correo se muestra el aviso")
        assertTrue("address = FEEDBACK_EMAIL" in settings, "el aviso muestra la misma dirección")
    }
}
