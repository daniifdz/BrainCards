package org.braincards.app.presentation.screens.settings

import braincards.composeapp.generated.resources.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guarda de los textos de Ajustes: la pantalla no contiene literales de interfaz (solo el asunto y el cuerpo del correo de comentarios,
 * que no son texto de pantalla) y cada clave de `strings.xml` tiene exactamente el texto que se veía antes de moverlos. Lee el código
 * fuente e ignora los comentarios.
 */
class SettingsStringsGuardTest {

    private fun file(relative: String): File {
        val full = "composeApp/src/$relative"
        val found = generateSequence(File("").absoluteFile) { it.parentFile }.take(6).map { File(it, full) }.firstOrNull { it.isFile }
        assertNotNull(found, "no se encuentra $full subiendo desde ${File("").absolutePath}")
        return found
    }

    private val screen: String
        get() = file("commonMain/kotlin/org/braincards/app/presentation/screens/settings/SettingsScreen.kt").readText()
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""(?m)//.*$"""), "")

    /** Texto de cada clave, tal como se veía en la pantalla antes de pasarlo a recursos. */
    private val expected = mapOf(
        "settings_title" to "Ajustes",
        "settings_back" to "Volver",
        "settings_account_header" to "Cuenta",
        "settings_signed_in_fallback" to "Sesión iniciada",
        "settings_sign_out" to "Cerrar sesión",
        "settings_delete_account" to "Eliminar cuenta",
        "settings_about_header" to "Acerca de",
        "settings_version_title" to "Versión de BrainCards",
        "settings_developer_title" to "Desarrollado por",
        "settings_developer_name" to "Daniel Fernández-Muñiz Arribas",
        "settings_feedback_title" to "Enviar comentarios",
        "settings_feedback_subtitle" to "Ayúdanos a mejorar",
        "settings_copyright_title" to "Copyright",
        "settings_copyright_text" to "© 2026 BrainCards. Todos los derechos reservados."
    )

    /** Únicos literales admitidos: el asunto y el cuerpo del correo (contenido saliente, no texto de interfaz). */
    private val allowedLiterals = setOf(
        "\"Feedback para BrainCards v\$APP_VERSION\"",
        "\"Hola, tengo una sugerencia/problema:\\n\\n\""
    )

    @Test
    fun la_pantalla_no_contiene_literales_de_interfaz() {
        val literals = Regex(""""(?:[^"\\\n]|\\.)*"""").findAll(screen).map { it.value }.filter { lit ->
            lit.any { it.isLetter() }
        }.toList()
        val unexpected = literals.filterNot { it in allowedLiterals }
        assertTrue(unexpected.isEmpty(), "literales en Ajustes que deberían estar en strings.xml: $unexpected")
    }

    @Test
    fun los_dos_literales_admitidos_siguen_siendo_los_del_correo() {
        val src = screen
        for (literal in allowedLiterals) {
            assertTrue(literal in src, "falta el literal del correo $literal (si se mueve a recursos, actualizar esta guarda)")
        }
    }

    @Test
    fun cada_clave_tiene_el_texto_exacto_de_antes() {
        val xml = file("commonMain/composeResources/values/strings.xml").readText()
        for ((key, text) in expected) {
            val value = Regex("""<string name="$key"(?: translatable="false")?>([^<]*)</string>""").find(xml)?.groupValues?.get(1)
            assertEquals(text, value, "texto de $key")
        }
    }

    @Test
    fun el_nombre_del_desarrollador_no_es_traducible() {
        val xml = file("commonMain/composeResources/values/strings.xml").readText()
        assertTrue("""<string name="settings_developer_name" translatable="false">""" in xml)
    }

    @Test
    fun la_pantalla_usa_cada_clave_una_vez_como_minimo() {
        val src = screen
        for (key in expected.keys) {
            assertTrue("Res.string.$key" in src, "SettingsScreen no usa Res.string.$key")
        }
    }

    @Test
    fun los_recursos_de_compose_resuelven_en_tiempo_de_ejecucion_el_texto_de_antes() = runBlocking {
        val resolved = mapOf(
            "settings_title" to getString(Res.string.settings_title),
            "settings_back" to getString(Res.string.settings_back),
            "settings_account_header" to getString(Res.string.settings_account_header),
            "settings_signed_in_fallback" to getString(Res.string.settings_signed_in_fallback),
            "settings_sign_out" to getString(Res.string.settings_sign_out),
            "settings_delete_account" to getString(Res.string.settings_delete_account),
            "settings_about_header" to getString(Res.string.settings_about_header),
            "settings_version_title" to getString(Res.string.settings_version_title),
            "settings_developer_title" to getString(Res.string.settings_developer_title),
            "settings_developer_name" to getString(Res.string.settings_developer_name),
            "settings_feedback_title" to getString(Res.string.settings_feedback_title),
            "settings_feedback_subtitle" to getString(Res.string.settings_feedback_subtitle),
            "settings_copyright_title" to getString(Res.string.settings_copyright_title),
            "settings_copyright_text" to getString(Res.string.settings_copyright_text)
        )
        assertEquals(expected, resolved)
    }
}
