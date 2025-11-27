package org.braincards.app.presentation.screens.studyStats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.braincards.app.domain.models.StudyStats
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyStatsScreen(
    onBackClick: () -> Unit,
    viewModel: StudyStatsVM = koinViewModel()
) {
    val stats by viewModel.stats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estadísticas de Progreso") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Total de Cartas", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "${stats.totalCards}",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (stats.totalCards == 0) {
                Text("No hay datos suficientes para mostrar el gráfico.", style = MaterialTheme.typography.bodyLarge)
            } else {
                // GRÁFICO CIRCULAR
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(250.dp)
                ) {
                    PieChart(
                        stats = stats,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // LEYENDA
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem(color = Color(0xFFBB86FC), text = "Nuevas (Sin empezar)", value = stats.newCards)
                    LegendItem(color = Color(0xFF03DAC5), text = "En Progreso (Repasando)", value = stats.learningCards)
                    LegendItem(color = Color(0xFF3700B3), text = "Dominadas (Memorizadas)", value = stats.matureCards)
                }
            }
        }
    }
}

@Composable
fun PieChart(stats: StudyStats, modifier: Modifier = Modifier) {
    val total = stats.totalCards.toFloat()
    if (total == 0f) return

    val newAngle = (stats.newCards / total) * 360f
    val learningAngle = (stats.learningCards / total) * 360f
    val matureAngle = (stats.matureCards / total) * 360f

    Canvas(modifier = modifier) {
        var startAngle = -90f
        val strokeWidth = 40.dp.toPx()

        // 1. Nuevas
        drawArc(
            color = Color(0xFFBB86FC),
            startAngle = startAngle,
            sweepAngle = newAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
        )
        startAngle += newAngle

        // 2. Aprendiendo
        drawArc(
            color = Color(0xFF03DAC5),
            startAngle = startAngle,
            sweepAngle = learningAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
        )
        startAngle += learningAngle


        drawArc(
            color = Color(0xFF3700B3),
            startAngle = startAngle,
            sweepAngle = matureAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
        )
    }
}

@Composable
fun LegendItem(color: Color, text: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(16.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = color
        ) {}
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(text = "$value", fontWeight = FontWeight.Bold)
    }
}