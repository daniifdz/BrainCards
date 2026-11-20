package org.braincards.app.presentation.screens.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import org.braincards.app.presentation.components.SampleDeckButton
import org.braincards.app.presentation.screens.decks.DecksEmpty
import org.braincards.app.presentation.screens.home.HomeContent
import org.braincards.app.presentation.screens.home.HomeUiState
import org.braincards.app.presentation.sync.SyncIndicator
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Dónde aparece «Crear mazo de ejemplo», comprobado sobre el render real: en el primer uso de Home (0 mazos) y en la Lista de mazos
 * vacía el botón se dibuja; en CUALQUIER otro estado de Home, aunque se le pase la acción, la pantalla queda idéntica píxel a píxel.
 */
class SampleDeckPlacementRenderTest {

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(widthDp: Int, content: @Composable () -> Unit): ByteArray {
        val scene = ImageComposeScene(widthDp, 900, Density(1f)) {
            BrainCardsTheme(darkTheme = false, animate = false) { Box(Modifier) { content() } }
        }
        try {
            repeat(30) { scene.render(it * 50_000_000L) }
            val bmp = Bitmap.makeFromImage(scene.render(2_000_000_000L))
            return bmp.readPixels(ImageInfo(bmp.width, bmp.height, ColorType.BGRA_8888, ColorAlphaType.PREMUL), bmp.width * 4)!!
        } finally {
            scene.close()
        }
    }

    private fun home(state: HomeUiState, width: Int, withAction: Boolean, creating: Boolean = false): ByteArray = render(width) {
        HomeContent(
            state = state, indicator = SyncIndicator.Offline, onRetry = {}, onNavigateToDecks = {}, onNavigateToStudy = {},
            onNavigateToFreeReview = {}, onNavigateToStats = {}, onPillClick = {}, onNavigateToSettings = {},
            sampleDeckCreating = creating, onCreateSampleDeck = if (withAction) ({}) else null
        )
    }

    @Test
    fun home_primer_uso_dibuja_el_boton_en_movil_y_en_escritorio() {
        listOf(360, 900).forEach { w ->
            assertFalse(
                home(HomeUiState.NoDecks, w, withAction = false).contentEquals(home(HomeUiState.NoDecks, w, withAction = true)),
                "a $w dp el primer uso debe llevar el botón"
            )
        }
    }

    @Test
    fun home_primer_uso_creando_se_ve_distinto_del_reposo() {
        assertFalse(
            home(HomeUiState.NoDecks, 900, withAction = true, creating = false)
                .contentEquals(home(HomeUiState.NoDecks, 900, withAction = true, creating = true))
        )
    }

    @Test
    fun home_en_los_demas_estados_no_cambia_aunque_se_pase_la_accion() {
        val others = listOf(HomeUiState.Loading, HomeUiState.Error, HomeUiState.NoCards, HomeUiState.Empty, HomeUiState.Content(3))
        for (state in others) for (w in listOf(360, 900)) {
            assertTrue(
                home(state, w, withAction = false).contentEquals(home(state, w, withAction = true)),
                "$state a $w dp no debe cambiar"
            )
        }
    }

    @Test
    fun la_lista_de_mazos_vacia_dibuja_el_boton() {
        listOf(360, 900).forEach { w ->
            val without = render(w) { DecksEmpty(onCreateClick = {}) }
            val with = render(w) { DecksEmpty(onCreateClick = {}, secondary = { SampleDeckButton(creating = false, onClick = {}) }) }
            assertFalse(without.contentEquals(with), "a $w dp la lista vacía debe llevar el botón")
        }
    }

    @Test
    fun la_lista_de_mazos_vacia_sin_segundo_boton_es_la_de_siempre() {
        val a = render(360) { DecksEmpty(onCreateClick = {}) }
        val b = render(360) { DecksEmpty(onCreateClick = {}, secondary = null) }
        assertTrue(a.contentEquals(b))
    }
}
