package org.braincards.app.presentation.components.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Fraccion de la altura util que ocupa la zona del simbolo (.auth-logo-zone { flex: 0 0 34% } de los mockups). */
private const val LOGO_ZONE_FRACTION = 0.34f

/** Por debajo de este ancho de ventana el dispositivo se considera "movil" (tamano de simbolo movil, ver [welcomeLogoSpec]). */
private val MOBILE_WIDTH = 600.dp

/** Ancho maximo de la columna de acciones (.auth-col { max-width: 420px }) y margen lateral (.auth-body: 28px). */
private val COLUMN_MAX_WIDTH = 420.dp
private val SIDE_PADDING = 28.dp

/** Separacion entre los hijos de la zona de acciones (.auth-action-zone { gap: 18px }) y bajo ella (.auth-body: 32px). */
private val ACTION_GAP = 18.dp
private val BOTTOM_PADDING = 32.dp

/**
 * Contenedor de la Bienvenida (solo ella; el resto de pantallas de auth siguen con [FormScaffold]):
 *
 *  - [hero]: simbolo de la marca en el tercio superior (34 % de la altura util), centrado; recibe el [WelcomeLogoSpec] (tamano
 *    segun el tamano de ventana SIN teclado, ver [welcomeLogoSpec]). La zona mide como minimo el 34 %, pero crece si el
 *    simbolo no cabe (fuente grande) en vez de solaparse con las acciones.
 *  - [content]: bloque de acciones, centrado verticalmente en el espacio restante.
 *  - Columna de [COLUMN_MAX_WIDTH] como maximo, centrada (Desktop / tablet).
 *  - Con el teclado abierto el centrado se SUSPENDE (el simbolo pasa a su altura natural y las acciones a la parte
 *    de arriba): el contenido se desplaza y `imePadding` deja el area visible justo encima del teclado, asi el campo
 *    con foco y el boton de envio quedan a la vista. El Snackbar tambien va por encima del teclado.
 *  - Si el contenido no cabe (movil pequeno, apaisado, ventana baja) hace scroll en vez de recortarse.
 */
@Composable
fun WelcomeScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    hero: @Composable ColumnScope.(logo: WelcomeLogoSpec) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it, Modifier.imePadding()) } }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Medida de ventana SIN teclado: este BoxWithConstraints esta ANTES del imePadding, asi el logo no cambia
            // de tamano al abrirse el teclado (el ancho nunca depende del IME; el alto, aqui, tampoco).
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val logo = welcomeLogoSpec(
                    widthDp = maxWidth.value,
                    heightDp = maxHeight.value,
                    isMobile = maxWidth < MOBILE_WIDTH
                )
                // imePadding FUERA del scroll: el area de scroll es la visible sobre el teclado.
                Box(modifier = Modifier.fillMaxSize().imePadding()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        val viewportHeight: Dp = maxHeight
                        val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                        val logoZoneHeight = viewportHeight * LOGO_ZONE_FRACTION

                        Column(
                            modifier = Modifier
                                .widthIn(max = COLUMN_MAX_WIDTH + SIDE_PADDING * 2)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = SIDE_PADDING)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (imeOpen) Modifier.padding(vertical = 20.dp)
                                        else Modifier.heightIn(min = logoZoneHeight)
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(logo.gapDp.dp, Alignment.CenterVertically)
                            ) { hero(logo) }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (imeOpen) Modifier
                                        else Modifier.heightIn(min = (viewportHeight - logoZoneHeight).coerceAtLeast(0.dp))
                                    )
                                    .padding(bottom = BOTTOM_PADDING),
                                verticalArrangement = Arrangement.spacedBy(
                                    ACTION_GAP,
                                    if (imeOpen) Alignment.Top else Alignment.CenterVertically
                                ),
                                content = content
                            )
                        }
                    }
                }
            }
        }
    }
}
