package org.braincards.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class SystemBarsPolicyTest {

    @Test
    fun con_entrypending_visible_los_iconos_son_claros_en_cualquier_tema() {
        assertEquals(StatusBarMode.LightIcons, statusBarMode(appDark = false, entryPendingVisible = true))
        assertEquals(StatusBarMode.LightIcons, statusBarMode(appDark = true, entryPendingVisible = true))
    }

    @Test
    fun sin_entrypending_siguen_al_tema_efectivo() {
        assertEquals(StatusBarMode.FollowTheme(dark = false), statusBarMode(appDark = false, entryPendingVisible = false))
        assertEquals(StatusBarMode.FollowTheme(dark = true), statusBarMode(appDark = true, entryPendingVisible = false))
    }
}
