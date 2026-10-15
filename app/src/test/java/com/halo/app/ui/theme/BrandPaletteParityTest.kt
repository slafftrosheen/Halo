package com.halo.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The identity contract is shared with HaloWeb/css/tokens.css. */
class BrandPaletteParityTest {
    @Test fun canonicalBrandTokensMatchTheWebsite() {
        assertEquals(Color(0xFF0CF2BA), HaloMint)
        assertEquals(Color(0xFF060330), HaloNightInk)
        assertEquals(Color(0xFF047A5E), HaloDeepMint)
        assertEquals(Color(0xFFFAFAF4), HaloCream)
        assertEquals(Color(0xFF4A4F63), HaloSlate)
        assertEquals(Color(0xFFFF9F6B), HaloSunriseApricot)
        assertEquals(Color(0xFFB4E6D6), HaloMintTint)
    }

    private fun contrast(a: Color, b: Color) =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) /
            (minOf(a.luminance(), b.luminance()) + 0.05f)

    @Test fun canonicalLightAndDarkSchemesKeepAccessibleTextAndActions() {
        listOf(false, true).forEach { dark ->
          HaloTone.entries.forEach { tone -> HaloAccent.entries.forEach { accent ->
            val colors = haloColorScheme(dark, HaloPalette(accent, tone))
            assertEquals("Tonal elevation must preserve canonical surfaces", colors.surface, colors.surfaceTint)
            listOf(
                colors.onBackground to colors.background,
                colors.onSurface to colors.surface,
                colors.onSurfaceVariant to colors.surfaceContainerHighest,
                colors.onPrimary to colors.primary,
                colors.onPrimaryContainer to colors.primaryContainer
            ).forEach { (foreground, background) ->
                assertTrue("AA contrast, dark=" + dark + ", tone=" + tone + ", accent=" + accent + ", ratio=" + contrast(foreground, background),
                    contrast(foreground, background) >= 4.5f)
            }
          } }
        }
    }

    @Test fun brandDefaultsMatchIntendedIdentitySurfaces() {
        val light = haloColorScheme(false, HaloPalette())
        val dark = haloColorScheme(true, HaloPalette())
        assertEquals(HaloCream, light.background)
        assertEquals(HaloNightInk, light.onBackground)
        assertEquals(HaloDeepMint, light.primary)
        assertEquals(HaloNightInk, dark.background)
        assertEquals(HaloCream, dark.onBackground)
        assertEquals(HaloMint, dark.primary)
    }
}
