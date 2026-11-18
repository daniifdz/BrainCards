package org.braincards.app.presentation.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** Medidas de los badges de Ajustes, tomadas de `ajustes.html`, `ajustes-mobile.html` y `ajustes-invitado.html`. */
class SettingsBadgeSpecTest {
    @Test
    fun badge_del_titulo_30_con_hueco_de_12() {
        assertEquals(30, SETTINGS_TITLE_BADGE_DP)
        assertEquals(12, SETTINGS_TITLE_BADGE_GAP_DP)
    }

    @Test
    fun badge_de_acerca_de_40() = assertEquals(40, SETTINGS_ABOUT_BADGE_DP)
}
