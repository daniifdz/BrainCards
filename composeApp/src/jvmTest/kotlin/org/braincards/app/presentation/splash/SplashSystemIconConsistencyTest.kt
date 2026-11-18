package org.braincards.app.presentation.splash

import org.braincards.app.presentation.screens.navigation.SPLASH_ANIMATE_ON_COLD_START
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El icono transparente del splash de Android >= 12 es un RECURSO y no lo gobierna el interruptor en codigo, asi que este test
 * exige que ambos esten de acuerdo: con SPLASH_ANIMATE_ON_COLD_START el override de values-v31 existe y apunta al icono
 * transparente; sin el, no existe. Tambien protege que por debajo de 31 el tema sigue mostrando el cerebro.
 */
class SplashSystemIconConsistencyTest {

    private val res = File("src/androidMain/res")
    private val base = File(res, "values/splash_system_icon.xml")
    private val v31 = File(res, "values-v31/splash_system_icon.xml")
    private val themes = File(res, "values/themes.xml")
    private val blank = File(res, "drawable/ic_splash_blank.xml")

    private fun aliasTo(target: String) =
        Regex("""name="splash_system_icon"\s+type="drawable">@drawable/$target<""")

    @Test
    fun por_defecto_y_por_debajo_de_31_el_icono_es_el_cerebro() {
        assertTrue(aliasTo("ic_splash_logo").containsMatchIn(base.readText()))
    }

    @Test
    fun el_tema_usa_el_alias_unico() {
        val text = themes.readText()
        assertTrue(text.contains("""<item name="windowSplashScreenAnimatedIcon">@drawable/splash_system_icon</item>"""))
        assertFalse(text.contains("@drawable/ic_splash_blank"), "el tema no debe apuntar directamente al icono transparente")
    }

    @Test
    fun el_override_de_values_v31_existe_si_y_solo_si_el_interruptor_esta_encendido() {
        assertEquals(SPLASH_ANIMATE_ON_COLD_START, v31.exists(), "values-v31/splash_system_icon.xml <-> SPLASH_ANIMATE_ON_COLD_START")
        if (v31.exists()) assertTrue(aliasTo("ic_splash_blank").containsMatchIn(v31.readText()))
    }

    @Test
    fun solo_values_v31_referencia_el_icono_transparente_y_es_transparente() {
        val referencing = res.walkTopDown()
            .filter { it.isFile && it.extension == "xml" && it != blank }
            .filter { it.readText().contains("@drawable/ic_splash_blank") }
            .map { it.invariantSeparatorsPath.substringAfter("res/") }
            .toList()
        assertEquals(if (SPLASH_ANIMATE_ON_COLD_START) listOf("values-v31/splash_system_icon.xml") else emptyList(), referencing)
        assertTrue(blank.readText().contains("""android:color="#00000000""""), "el icono es totalmente transparente")
    }
}
