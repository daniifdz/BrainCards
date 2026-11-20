package org.braincards.app.presentation.screens.sample

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guarda del CABLEADO del mazo de ejemplo (no hay `compose.uiTest` ni NavController de prueba): la ruta lleva la marca con valor por
 * defecto, Home y la Lista de mazos navegan al contenido con ella y atienden el resultado con el mismo componente, Home usa un
 * Snackbar superpuesto (sin Scaffold) y el contenido del mazo muestra «Mazo de ejemplo creado» una sola vez. Lee el código fuente e
 * ignora los comentarios. También fija los textos de `strings.xml`.
 */
class SampleDeckWiringGuardTest {

    private fun file(relative: String): File {
        val full = "composeApp/src/$relative"
        val found = generateSequence(File("").absoluteFile) { it.parentFile }.take(6).map { File(it, full) }.firstOrNull { it.isFile }
        assertNotNull(found, "no se encuentra $full subiendo desde ${File("").absolutePath}")
        return found
    }

    private fun source(relative: String): String =
        file(relative).readText().replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""(?m)//.*$"""), "")

    private fun count(source: String, needle: String) = Regex(Regex.escape(needle)).findAll(source).count()

    private val app get() = source("commonMain/kotlin/org/braincards/app/App.kt")
    private val screens = "commonMain/kotlin/org/braincards/app/presentation/screens"

    @Test
    fun la_ruta_del_contenido_lleva_la_marca_con_valor_por_defecto() {
        val nav = source("$screens/navigation/ScreenNavigator.kt")
        assertTrue("data class FlashcardsList(val deckId: String, val sampleCreated: Boolean = false)" in nav)
    }

    @Test
    fun home_y_la_lista_navegan_al_contenido_marcando_el_mazo_de_ejemplo() {
        val src = app
        assertEquals(2, count(src, "Screen.FlashcardsList(deckId, sampleCreated = true)"), "Home y Lista de mazos")
        assertEquals(1, count(src, "Screen.FlashcardsList(deckId))"), "el toque normal en un mazo no lleva marca")
        assertTrue("onNavigateToSampleDeck = " in src)
        assertTrue("onSampleDeckReady = " in src)
        assertTrue("sampleCreated = route.sampleCreated" in src, "el contenido recibe la marca de la ruta")
    }

    @Test
    fun home_y_la_lista_atienden_el_resultado_con_el_mismo_componente_y_la_regla_de_visibilidad() {
        val home = source("$screens/home/homeScreen.kt")
        val decks = source("$screens/decks/DecksScreen.kt")
        assertTrue("CollectSampleDeckEvents(sampleDeckViewModel, snackbarHostState, onNavigateToSampleDeck, onHasDecks = onNavigateToDecks)" in home)
        assertTrue("CollectSampleDeckEvents(sampleDeckViewModel, snackbarHostState, onSampleDeckReady, onHasDecks = {})" in decks)
        assertTrue("homeShowsSampleDeck(state)" in home)
        assertTrue("decksShowSampleDeck(current)" in decks)
    }

    @Test
    fun home_usa_un_snackbar_superpuesto_y_no_un_scaffold() {
        val home = source("$screens/home/homeScreen.kt")
        assertTrue("SnackbarHost(snackbarHostState" in home)
        assertFalse("Scaffold(" in home, "un Scaffold cambiaría márgenes e insets de Home")
    }

    @Test
    fun el_resultado_navega_si_esta_listo_y_avisa_si_falla() {
        val events = source("$screens/sample/SampleDeckEvents.kt")
        assertTrue("is SampleDeckEvent.Ready -> currentOnReady(event.deckId)" in events)
        assertTrue("SampleDeckEvent.Failed -> snackbarHostState.showSnackbar(failedText)" in events)
        assertTrue("SampleDeckEvent.AlreadyHasDecks -> currentOnHasDecks()" in events, "con mazos previos se lleva a la Lista")
        assertTrue("Res.string.sample_deck_failed" in events)
    }

    @Test
    fun el_contenido_del_mazo_muestra_el_aviso_de_exito_una_sola_vez() {
        val content = source("$screens/flashcards/DeckContentScreen.kt")
        assertTrue("sampleCreated: Boolean = false" in content)
        assertTrue("var sampleNoticeShown by rememberSaveable { mutableStateOf(false) }" in content)
        assertTrue("if (sampleCreated && !sampleNoticeShown)" in content)
        assertTrue("snackbarHostState.showSnackbar(sampleCreatedText)" in content)
        assertTrue("Res.string.sample_deck_created" in content)
    }

    @Test
    fun los_textos_de_la_interfaz_son_los_del_diseno() {
        val xml = file("commonMain/composeResources/values/strings.xml").readText()
        val expected = mapOf(
            "sample_deck_create" to "Crear mazo de ejemplo",
            "sample_deck_creating" to "Creando…",
            "sample_deck_created" to "Mazo de ejemplo creado",
            "sample_deck_failed" to "No se pudo crear el mazo de ejemplo. Inténtalo de nuevo.",
            "sample_deck_name" to "Primeros pasos",
            "sample_deck_topic_study" to "Cómo estudiar con BrainCards",
            "sample_deck_topic_organize" to "Cómo organizar y sincronizar tus mazos"
        )
        for ((key, text) in expected) {
            val value = Regex("""<string name="$key">([^<]*)</string>""").find(xml)?.groupValues?.get(1)
            assertEquals(text, value, "texto de $key")
        }
    }
}
