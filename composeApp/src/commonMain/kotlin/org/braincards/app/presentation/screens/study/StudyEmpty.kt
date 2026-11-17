package org.braincards.app.presentation.screens.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.study_empty_cta
import braincards.composeapp.generated.resources.study_empty_text
import braincards.composeapp.generated.resources.study_empty_title
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.compose.resources.stringResource

/** Estado vacio «¡Estamos al día!» (mockup `estudio-vacio.html`): icono en chip, titulo, texto y un unico boton. */
@Composable
fun StudyEmpty(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val extra = BrainCardsTheme.extraColors
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(24.dp), color = extra.chipTealBg, contentColor = extra.chipTeal) {
                Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(44.dp))
                }
            }
            Text(
                text = stringResource(Res.string.study_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(Res.string.study_empty_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 260.dp)
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(Res.string.study_empty_cta), fontSize = 15.sp)
            }
        }
    }
}
