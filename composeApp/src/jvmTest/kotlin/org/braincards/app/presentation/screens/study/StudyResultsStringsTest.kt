package org.braincards.app.presentation.screens.study

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * «N repasos»: el plural vive en strings.xml, así que se comprueba el propio recurso: singular «1 repaso» y plural «N repasos»
 * (Compose Resources elige la cantidad `one` para 1 y `other` para el resto).
 */
class StudyResultsStringsTest {

    private fun stringsXml(): String {
        val relative = "composeApp/src/commonMain/composeResources/values/strings.xml"
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .take(6)
            .map { File(it, relative) }
            .firstOrNull { it.isFile }
        assertNotNull(file, "no se encuentra $relative subiendo desde ${File("").absolutePath}")
        return file.readText()
    }

    private fun item(quantity: String): String {
        val block = Regex("""<plurals name="study_results_count">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
            .find(stringsXml())?.groupValues?.get(1)
        assertNotNull(block, "plural study_results_count")
        val text = Regex("""<item quantity="$quantity">([^<]*)</item>""").find(block)?.groupValues?.get(1)
        assertNotNull(text, "item $quantity")
        return text
    }

    @Test
    fun el_singular_es_1_repaso() {
        assertEquals("%1\$d repaso", item("one"))
    }

    @Test
    fun el_plural_es_N_repasos() {
        assertEquals("%1\$d repasos", item("other"))
    }
}
