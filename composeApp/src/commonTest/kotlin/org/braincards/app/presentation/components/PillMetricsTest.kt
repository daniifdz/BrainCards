package org.braincards.app.presentation.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Medidas de la pastilla compartida. Son EXACTAMENTE las que tenia la pildora de sincronizacion de Home antes de extraer el `Pill`
 * (10 / 6 / 6 / 12 compacta y 14 / 8 / 8 / 13 normal, icono de 16 dp) y las de `.pill.sm` / `.pill` de los mockups: si cambian, Home cambia.
 */
class PillMetricsTest {

    @Test
    fun la_pastilla_compacta_es_la_pill_sm_de_los_mockups() {
        assertEquals(PillMetrics(horizontalDp = 10, verticalDp = 6, gapDp = 6, fontSp = 12), pillMetrics(compact = true))
    }

    @Test
    fun la_pastilla_normal_es_la_pill_de_los_mockups() {
        assertEquals(PillMetrics(horizontalDp = 14, verticalDp = 8, gapDp = 8, fontSp = 13), pillMetrics(compact = false))
    }

    @Test
    fun el_icono_de_la_pildora_de_sync_sigue_midiendo_16_y_el_del_chip_13() {
        assertEquals(16.dp, PILL_SYNC_ICON_SIZE)
        assertEquals(13.dp, PILL_CHIP_ICON_SIZE)
    }
}
