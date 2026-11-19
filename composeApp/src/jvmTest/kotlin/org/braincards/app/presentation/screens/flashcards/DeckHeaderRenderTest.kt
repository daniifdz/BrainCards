package org.braincards.app.presentation.screens.flashcards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.braincards.app.presentation.theme.BrainCardsTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cabecera de Contenido de un mazo con sus 4 botones («Estudiar», «Repaso libre», «Nuevo tema», «Nueva tarjeta»), renderizada de verdad
 * (ImageComposeScene, 1 px = 1 dp) y MEDIDA con `inspect`: a 360 dp, con la fuente ampliada (1,3 y 2,0) y a ancho de escritorio. Comprueba
 * que ningun boton se sale del ancho, que no se solapan, que el texto no se parte en dos lineas, que las dos acciones de estudio van antes que
 * las de creacion y que cada boton llama a SU accion.
 */
class DeckHeaderRenderTest {

    private val labels = DeckHeaderLabels(
        study = "Estudiar", studyDescription = "Estudiar mazo", freeReview = "Repaso libre",
        newTopic = "Nuevo tema", newCard = "Nueva tarjeta", back = "Volver a Mis Mazos"
    )

    private class Counts {
        var study = 0
        var free = 0
        var topic = 0
        var card = 0
    }

    private class Layout(val parts: Map<DeckHeaderPart, Rect>)

    @OptIn(ExperimentalComposeUiApi::class)
    private fun layoutOf(
        widthDp: Int,
        fontScale: Float = 1f,
        showStudy: Boolean = true,
        showFreeReview: Boolean = true,
        title: String = "Vocabulario — Inglés B2",
        counts: Counts = Counts(),
        click: DeckHeaderPart? = null
    ): Layout {
        val parts = mutableMapOf<DeckHeaderPart, Rect>()
        val scene = ImageComposeScene(widthDp, 700, Density(1f, fontScale)) {
            BrainCardsTheme(darkTheme = false, animate = false) {
                Box(Modifier.width(widthDp.dp)) {
                    DeckHeaderContent(
                        deckName = title, labels = labels, showStudy = showStudy, showFreeReview = showFreeReview,
                        onBack = {}, onNewTopic = { counts.topic++ }, onNewCard = { counts.card++ },
                        onStudy = { counts.study++ }, onFreeReview = { counts.free++ },
                        inspect = { part, rect -> parts[part] = rect }
                    )
                }
            }
        }
        try {
            scene.render()
            scene.render()
            if (click != null) {
                val c = parts.getValue(click).center
                scene.sendPointerEvent(PointerEventType.Press, c)
                scene.render()
                scene.sendPointerEvent(PointerEventType.Release, c)
                scene.render()
            }
            return Layout(parts.toMap())
        } finally {
            scene.close()
        }
    }

    private val buttons = listOf(DeckHeaderPart.Study, DeckHeaderPart.FreeReview, DeckHeaderPart.NewTopic, DeckHeaderPart.NewCard)

    private fun Layout.buttonRects() = buttons.mapNotNull { p -> parts[p]?.let { p to it } }

    private fun assertNothingCutOrOverlapping(layout: Layout, widthDp: Int, label: String) {
        val rects = layout.buttonRects()
        for ((part, r) in rects) {
            assertTrue(r.left >= 0f && r.right <= widthDp + 0.5f, "$label: $part se sale del ancho: ${r.left}..${r.right} de $widthDp dp")
            assertTrue(r.width > 0f && r.height > 0f, "$label: $part sin tamano")
        }
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            assertFalse(rects[i].second.overlaps(rects[j].second), "$label: ${rects[i].first} y ${rects[j].first} se solapan: ${rects[i].second} / ${rects[j].second}")
        }
        val title = layout.parts.getValue(DeckHeaderPart.TitleRow)
        assertTrue(title.left >= 0f && title.right <= widthDp + 0.5f, "$label: el titulo se sale del ancho")
        for ((part, r) in rects) assertFalse(title.overlaps(r), "$label: el titulo se solapa con $part")
    }

    private fun assertSingleLine(layout: Layout, label: String) {
        val heights = layout.buttonRects().map { it.second.height }
        assertTrue(heights.max() - heights.min() <= 1f, "$label: alturas distintas $heights (algun texto se parte en 2 lineas)")
    }

    @Test
    fun a_360_dp_aparecen_los_4_botones_sin_salirse_ni_solaparse() {
        val l = layoutOf(360)
        assertEquals(buttons.toSet(), l.buttonRects().map { it.first }.toSet(), "los 4 botones")
        assertNothingCutOrOverlapping(l, 360, "360 dp")
        assertSingleLine(l, "360 dp")
    }

    @Test
    fun a_360_dp_las_acciones_de_estudio_van_juntas_y_antes_que_las_de_creacion() {
        val l = layoutOf(360)
        val study = l.parts.getValue(DeckHeaderPart.Study)
        val free = l.parts.getValue(DeckHeaderPart.FreeReview)
        val topic = l.parts.getValue(DeckHeaderPart.NewTopic)
        val card = l.parts.getValue(DeckHeaderPart.NewCard)
        assertEquals(study.top, free.top, "«Estudiar» y «Repaso libre» en la misma fila")
        assertTrue(study.left < free.left, "«Repaso libre» justo despues de «Estudiar»")
        assertEquals(topic.top, card.top, "«Nuevo tema» y «Nueva tarjeta» en la fila siguiente")
        assertTrue(topic.top > study.bottom - 1f, "las de creacion en una fila POSTERIOR a las de estudio")
        val order = l.buttonRects().sortedWith(compareBy({ it.second.top }, { it.second.left })).map { it.first }
        assertEquals(buttons, order, "orden de lectura: Estudiar, Repaso libre, Nuevo tema, Nueva tarjeta")
    }

    @Test
    fun a_360_dp_el_titulo_va_encima_de_los_botones_porque_la_cabecera_es_compacta() {
        val l = layoutOf(360)
        val title = l.parts.getValue(DeckHeaderPart.TitleRow)
        assertTrue(l.buttonRects().all { it.second.top >= title.bottom - 0.5f }, "botones bajo el titulo")
    }

    @Test
    fun un_nombre_de_mazo_larguisimo_no_empuja_ni_tapa_los_botones() {
        val l = layoutOf(360, title = "Un mazo con un nombre ridiculamente largo que no cabe nunca en una sola linea de la cabecera")
        assertNothingCutOrOverlapping(l, 360, "titulo largo")
        assertEquals(buttons.toSet(), l.buttonRects().map { it.first }.toSet())
    }

    @Test
    fun con_fuente_1_3_la_cabecera_sigue_completa_sin_cortes_ni_solapes() {
        val normal = layoutOf(360, fontScale = 1f)
        val big = layoutOf(360, fontScale = 1.3f)
        assertTrue(big.parts.getValue(DeckHeaderPart.NewCard).width > normal.parts.getValue(DeckHeaderPart.NewCard).width,
            "la fuente ampliada se aplica: el boton es mas ancho")
        assertEquals(buttons.toSet(), big.buttonRects().map { it.first }.toSet())
        assertNothingCutOrOverlapping(big, 360, "fuente 1,3")
        assertSingleLine(big, "fuente 1,3")
        val order = big.buttonRects().sortedWith(compareBy({ it.second.top }, { it.second.left })).map { it.first }
        assertEquals(buttons, order, "mismo orden con la fuente grande")
    }

    @Test
    fun con_fuente_2_0_nada_se_sale_del_ancho_ni_se_solapa_aunque_envuelva_mas() {
        val l = layoutOf(360, fontScale = 2f)
        assertEquals(buttons.toSet(), l.buttonRects().map { it.first }.toSet())
        assertNothingCutOrOverlapping(l, 360, "fuente 2,0")
        val order = l.buttonRects().sortedWith(compareBy({ it.second.top }, { it.second.left })).map { it.first }
        assertEquals(buttons, order)
    }

    @Test
    fun a_320_dp_el_movil_mas_estrecho_tambien_cabe() {
        val l = layoutOf(320)
        assertNothingCutOrOverlapping(l, 320, "320 dp")
        assertSingleLine(l, "320 dp")
    }

    @Test
    fun a_ancho_de_escritorio_los_4_botones_van_en_una_sola_fila_junto_al_titulo() {
        val l = layoutOf(1000)
        assertNothingCutOrOverlapping(l, 1000, "1000 dp")
        assertEquals(1, l.buttonRects().map { it.second.top }.toSet().size, "una sola fila de botones")
        val title = l.parts.getValue(DeckHeaderPart.TitleRow)
        assertTrue(l.buttonRects().all { it.second.left >= title.right - 0.5f }, "los botones a la derecha del titulo")
    }

    @Test
    fun sin_repaso_libre_la_cabecera_tiene_3_botones_y_sin_estudiar_tampoco_hay_ninguno_de_estudio() {
        val three = layoutOf(360, showFreeReview = false)
        assertEquals(setOf(DeckHeaderPart.Study, DeckHeaderPart.NewTopic, DeckHeaderPart.NewCard), three.buttonRects().map { it.first }.toSet())
        assertNothingCutOrOverlapping(three, 360, "3 botones")

        val none = layoutOf(360, showStudy = false, showFreeReview = false)
        assertEquals(setOf(DeckHeaderPart.NewTopic, DeckHeaderPart.NewCard), none.buttonRects().map { it.first }.toSet(), "mazo vacio: se ocultan los dos de estudio a la vez")
    }

    @Test
    fun cada_boton_llama_solo_a_su_accion() {
        val expected = mapOf(
            DeckHeaderPart.Study to listOf(1, 0, 0, 0),
            DeckHeaderPart.FreeReview to listOf(0, 1, 0, 0),
            DeckHeaderPart.NewTopic to listOf(0, 0, 1, 0),
            DeckHeaderPart.NewCard to listOf(0, 0, 0, 1)
        )
        for ((part, want) in expected) {
            val c = Counts()
            layoutOf(360, counts = c, click = part)
            assertEquals(want, listOf(c.study, c.free, c.topic, c.card), "al pulsar $part")
        }
    }
}
