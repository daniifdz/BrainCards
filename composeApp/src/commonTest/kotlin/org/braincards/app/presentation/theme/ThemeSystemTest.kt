package org.braincards.app.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.braincards.app.presentation.screens.settings.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Completitud del ColorScheme, mapeo de roles, modo oscuro efectivo, formas y tipografia. */
class ThemeSystemTest {

    /** Los 36 roles del ColorScheme de Material3 (1.8), por nombre. */
    private fun ColorScheme.roles(): Map<String, Color> = linkedMapOf(
        "primary" to primary, "onPrimary" to onPrimary, "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer, "inversePrimary" to inversePrimary,
        "secondary" to secondary, "onSecondary" to onSecondary, "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary, "onTertiary" to onTertiary, "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
        "background" to background, "onBackground" to onBackground,
        "surface" to surface, "onSurface" to onSurface, "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant, "surfaceTint" to surfaceTint,
        "inverseSurface" to inverseSurface, "inverseOnSurface" to inverseOnSurface,
        "error" to error, "onError" to onError, "errorContainer" to errorContainer, "onErrorContainer" to onErrorContainer,
        "outline" to outline, "outlineVariant" to outlineVariant, "scrim" to scrim,
        "surfaceBright" to surfaceBright, "surfaceDim" to surfaceDim, "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh, "surfaceContainerHighest" to surfaceContainerHighest,
        "surfaceContainerLow" to surfaceContainerLow, "surfaceContainerLowest" to surfaceContainerLowest
    )

    // Roles que pueden coincidir con el esquema base de Material por coincidencia de valor, no por olvido: el negro del scrim, los
    // blancos (brandText / surface en claro) y el rojo de error B3261E que la paleta comparte con el base.
    private val allowedEqualToBaselineLight = setOf(
        "scrim", "onPrimary", "onSecondary", "onTertiary", "onError", "error", "surfaceContainerLowest"
    )
    private val allowedEqualToBaselineDark = setOf("scrim")

    @Test
    fun el_esquema_tiene_los_36_roles_y_todos_son_opacos_salvo_surfaceTint_y_outlineVariant() {
        for ((name, scheme) in listOf("claro" to LightColorScheme, "oscuro" to DarkColorScheme)) {
            val roles = scheme.roles()
            assertEquals(36, roles.size, "$name: 36 roles")
            roles.forEach { (role, color) ->
                when (role) {
                    "surfaceTint" -> assertEquals(Color.Transparent, color, "$name: surfaceTint transparente (sin tinte tonal)")
                    "outlineVariant" -> assertTrue(color.alpha < 1f && color.alpha > 0f, "$name: outlineVariant es el borde translucido")
                    else -> assertEquals(1f, color.alpha, "$name: $role debe ser opaco")
                }
            }
        }
    }

    @Test
    fun ningun_rol_se_queda_en_el_valor_base_de_material_salvo_los_permitidos() {
        val cases = listOf(
            Triple("claro", LightColorScheme to lightColorScheme(), allowedEqualToBaselineLight),
            Triple("oscuro", DarkColorScheme to darkColorScheme(), allowedEqualToBaselineDark)
        )
        for ((name, pair, allowed) in cases) {
            val (ours, baseline) = pair
            val base = baseline.roles()
            val equal = ours.roles().filter { (role, color) -> base.getValue(role) == color }.keys
            assertEquals(allowed, equal, "$name: roles identicos al esquema base de Material (se colarian en pantalla)")
        }
    }

    @Test
    fun mapeo_de_roles_segun_el_handoff_y_las_decisiones_D1_D3() {
        for ((s, p) in listOf(LightColorScheme to LightPalette, DarkColorScheme to DarkPalette)) {
            assertEquals(p.brand, s.primary)
            assertEquals(p.brandText, s.onPrimary)
            assertEquals(p.chipTeal, s.secondary)
            assertEquals(p.chipTealBg, s.secondaryContainer)
            assertEquals(p.textSecondary, s.tertiary)
            assertEquals(p.errorColor, s.error)
            assertEquals(p.errorBg, s.errorContainer)
            assertEquals(p.ground, s.background)
            assertEquals(p.surface, s.surface)
            assertEquals(p.border, s.outlineVariant)
            assertEquals(p.text, s.onBackground)
            assertEquals(p.text, s.onSurface)
            assertEquals(p.textSecondary, s.onSurfaceVariant)
            assertEquals(p.outline, s.outline)
            listOf(
                s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest,
                s.surfaceContainerLow, s.surfaceContainerLowest, s.surfaceBright
            ).forEach { assertEquals(p.surface, it, "surfaceContainer* = surface (sin diferencias tonales)") }
        }
        assertEquals(DarkPalette.brand, LightColorScheme.inversePrimary)
        assertEquals(LightPalette.brand, DarkColorScheme.inversePrimary)
        assertNotEquals(LightPalette.outline, LightPalette.border, "outline no es el border decorativo")
    }

    @Test
    fun los_colores_fuera_de_rol_van_en_BrainCardsExtraColors() {
        for (p in listOf(LightPalette, DarkPalette)) {
            val e = p.toExtraColors()
            assertEquals(p.cta, e.cta)
            assertEquals(p.ctaText, e.ctaText)
            assertEquals(p.chipTeal, e.chipTeal)
            assertEquals(p.chipTealBg, e.chipTealBg)
            assertEquals(p.chipGray, e.chipGray)
            assertEquals(p.chipGrayBg, e.chipGrayBg)
            assertEquals(p.brandChip, e.brandChip)
        }
    }

    @Test
    fun el_tema_oscuro_efectivo_respeta_AppTheme_SYSTEM_LIGHT_DARK() {
        for (systemDark in listOf(false, true)) {
            assertEquals(systemDark, resolveDarkTheme(AppTheme.SYSTEM, systemDark))
            assertEquals(systemDark, resolveDarkTheme(null, systemDark), "sin valor todavia = SYSTEM")
            assertEquals(false, resolveDarkTheme(AppTheme.LIGHT, systemDark))
            assertEquals(true, resolveDarkTheme(AppTheme.DARK, systemDark))
        }
    }

    @Test
    fun las_formas_son_12_12_16_18_20_dp() {
        assertEquals(12.dp, BrainCardsRadii.extraSmall)
        assertEquals(12.dp, BrainCardsRadii.small)
        assertEquals(16.dp, BrainCardsRadii.medium)
        assertEquals(18.dp, BrainCardsRadii.large)
        assertEquals(20.dp, BrainCardsRadii.extraLarge)
    }

    @Test
    fun sin_sombras_borde_de_1_dp_y_elevacion_0() {
        assertEquals(1.dp, BrainCardsDefaults.borderWidth)
        assertEquals(0.dp, BrainCardsDefaults.elevation)
        assertEquals(0.dp, BrainCardsDefaults.tonalElevation)
    }

    @Test
    fun la_tipografia_aplica_la_familia_a_los_15_estilos() {
        val family = FontFamily.Serif
        val t: Typography = Typography().withFontFamily(family)
        val styles = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall
        )
        assertEquals(15, styles.size)
        styles.forEach { assertEquals(family, it.fontFamily) }
        // Solo cambia la familia: tamanos y pesos de M3 se conservan.
        assertEquals(Typography().bodyLarge.fontSize, t.bodyLarge.fontSize)
        assertEquals(Typography().titleMedium.fontWeight, t.titleMedium.fontWeight)
    }
}
