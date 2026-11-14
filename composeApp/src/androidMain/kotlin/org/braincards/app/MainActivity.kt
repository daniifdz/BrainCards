package org.braincards.app

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.braincards.app.presentation.screens.settings.ThemeViewModel
import org.braincards.app.presentation.theme.resolveDarkTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen() // ANTES de super.onCreate()
        // Hasta leer el tema de la app, las barras siguen al sistema; en cuanto se conoce AppTheme se reajustan.
        applySystemBars(isSystemDark())
        super.onCreate(savedInstanceState)

        var isThemeReady = false
        splashScreen.setKeepOnScreenCondition { !isThemeReady }
        lifecycleScope.launch {
            themeViewModel.theme.first { it != null }
            isThemeReady = true
        }
        lifecycleScope.launch {
            themeViewModel.theme.filterNotNull().collect { appTheme ->
                applySystemBars(resolveDarkTheme(appTheme, isSystemDark()))
            }
        }

        setContent {
            App()
        }
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /**
     * Barras del sistema segun el tema EFECTIVO de la app (no solo el del sistema): con la app forzada a oscuro y el
     * sistema en claro, los iconos de la barra de estado deben ser claros. Barra de estado transparente; barra de
     * navegacion con los mismos scrims que usa enableEdgeToEdge() por defecto.
     */
    private fun applySystemBars(dark: Boolean) {
        val detectDarkMode: (Resources) -> Boolean = { dark }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT, detectDarkMode),
            navigationBarStyle = SystemBarStyle.auto(NAV_LIGHT_SCRIM, NAV_DARK_SCRIM, detectDarkMode)
        )
    }

    private companion object {
        val NAV_LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val NAV_DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}