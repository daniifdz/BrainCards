package org.braincards.app

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
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
import kotlinx.coroutines.withTimeoutOrNull
import org.braincards.app.platform.ProcessColdStart
import org.braincards.app.platform.StatusBarMode
import org.braincards.app.platform.SystemBarsHost
import org.braincards.app.platform.statusBarMode
import org.braincards.app.presentation.screens.navigation.AppEntryViewModel
import org.braincards.app.presentation.screens.navigation.ENTRY_SPLASH_MAX_WAIT_MS
import org.braincards.app.presentation.screens.navigation.EntryRoute
import org.braincards.app.presentation.screens.navigation.SPLASH_ANIMATE_ON_COLD_START
import org.braincards.app.presentation.screens.navigation.SplashExitSignal
import org.braincards.app.presentation.screens.navigation.shouldPlayColdStartSplash
import org.braincards.app.presentation.screens.settings.ThemeViewModel
import org.braincards.app.presentation.theme.resolveDarkTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity(), SystemBarsHost {

    private var lastDark = false
    private var entryPendingVisible = false

    private val themeViewModel: ThemeViewModel by viewModel()

    // Activity-scoped, la misma instancia que usa App() (igual que themeViewModel).
    private val entryViewModel: AppEntryViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen() // ANTES de super.onCreate()
        // Arranque en frío en Android >= 12: el splash del sistema es solo morado (values-v31) y EntryPending anima el cerebro
        // cuando el splash sale. La bandera de proceso se consume siempre (aunque no se use) para que solo valga la primera vez.
        val coldStartSplash = if (shouldPlayColdStartSplash(
                enabled = SPLASH_ANIMATE_ON_COLD_START,
                sdkInt = Build.VERSION.SDK_INT,
                hasSavedState = savedInstanceState != null,
                freshProcess = ProcessColdStart.consume()
            )
        ) SplashExitSignal() else null
        if (coldStartSplash != null) {
            // Salida instantanea (sin animacion del sistema): quita el splash y avisa para arrancar la coreografia.
            splashScreen.setOnExitAnimationListener { splashView ->
                splashView.remove()
                coldStartSplash.fire()
            }
        }
        // Hasta leer el tema de la app, las barras siguen al sistema; en cuanto se conoce AppTheme se reajustan.
        applySystemBars(isSystemDark())
        super.onCreate(savedInstanceState)

        var isThemeReady = false
        var isEntryReady = false
        splashScreen.setKeepOnScreenCondition { !(isThemeReady && isEntryReady) }
        lifecycleScope.launch {
            themeViewModel.theme.first { it != null }
            isThemeReady = true
        }
        // El splash espera a saber a donde arrancar (auth + flag de modo local), con un tope: pasado el tope, App()
        // muestra EntryPendingScreen (logo) hasta que se resuelva; nunca Bienvenida ni Home antes de tiempo.
        lifecycleScope.launch {
            withTimeoutOrNull(ENTRY_SPLASH_MAX_WAIT_MS) { entryViewModel.startRoute.first { it != EntryRoute.Pending } }
            isEntryReady = true
        }
        lifecycleScope.launch {
            themeViewModel.theme.filterNotNull().collect { appTheme ->
                applySystemBars(resolveDarkTheme(appTheme, isSystemDark()))
            }
        }

        setContent {
            App(coldStartSplash = coldStartSplash)
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
        lastDark = dark
        val detectDarkMode: (Resources) -> Boolean = { dark }
        // Con EntryPending visible (morado fijo) los iconos de la barra de estado son claros; si no, siguen al tema efectivo.
        val statusBarStyle = when (statusBarMode(dark, entryPendingVisible)) {
            StatusBarMode.LightIcons -> SystemBarStyle.dark(Color.TRANSPARENT)
            is StatusBarMode.FollowTheme -> SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT, detectDarkMode)
        }
        enableEdgeToEdge(
            statusBarStyle = statusBarStyle,
            navigationBarStyle = SystemBarStyle.auto(NAV_LIGHT_SCRIM, NAV_DARK_SCRIM, detectDarkMode)
        )
    }

    /** [SystemBarsHost]: EntryPending se muestra / empieza su fundido. Unico punto que reaplica las barras (nada las pisa). */
    override fun setEntryPendingVisible(visible: Boolean) {
        if (visible == entryPendingVisible) return
        entryPendingVisible = visible
        applySystemBars(lastDark)
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