package org.braincards.app.presentation.screens.auth

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.auth_back
import org.jetbrains.compose.resources.stringResource

/**
 * Cabecera comun de las cuatro pantallas de auth (v4.4): solo la flecha de vuelta, sin titulo. Se deshabilita
 * mientras hay un envio en curso ([enabled] = false), como dibujan los mockups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthTopBar(onBack: () -> Unit, enabled: Boolean = true) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.auth_back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}
