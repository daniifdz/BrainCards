package org.braincards.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.braincards.app.platform.AndroidContext
import org.braincards.app.presentation.screens.settings.ThemeViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen() // ANTES de super.onCreate()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AndroidContext.setContext(applicationContext)

        var isThemeReady = false
        splashScreen.setKeepOnScreenCondition { !isThemeReady }
        lifecycleScope.launch {
            themeViewModel.theme.first { it != null }
            isThemeReady = true
        }

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}