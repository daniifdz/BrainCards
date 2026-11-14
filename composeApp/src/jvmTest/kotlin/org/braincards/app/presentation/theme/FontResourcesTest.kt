package org.braincards.app.presentation.theme

import braincards.composeapp.generated.resources.Res
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Las seis fuentes estan empaquetadas como recursos y son TTF validos (cabecera sfnt 0x00010000). */
class FontResourcesTest {

    private val fonts = listOf(
        "fredoka_semibold", "fredoka_bold",
        "plusjakartasans_regular", "plusjakartasans_medium", "plusjakartasans_semibold", "plusjakartasans_bold"
    )

    @Test
    fun las_seis_fuentes_se_leen_como_recurso_y_son_ttf() = runBlocking {
        for (name in fonts) {
            val bytes = Res.readBytes("font/$name.ttf")
            assertTrue(bytes.size > 10_000, "$name: tamano razonable (${bytes.size})")
            val magic = (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (bytes[i].toLong() and 0xFF) }
            assertEquals(0x00010000L, magic, "$name: cabecera TrueType")
        }
    }
}
