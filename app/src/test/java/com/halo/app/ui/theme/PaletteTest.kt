package com.halo.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    private fun contrast(a: Color, b: Color): Float = (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
    @Test fun `every colour and tone in both themes preserves body and action contrast`() {
        listOf(false, true).forEach { dark -> HaloAccent.entries.forEach { accent -> HaloTone.entries.forEach { tone ->
            val scheme = haloColorScheme(dark, HaloPalette(accent, tone))
            listOf(scheme.onPrimary to scheme.primary, scheme.onSurface to scheme.surface,
                scheme.onBackground to scheme.background, scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSurfaceVariant to scheme.surfaceContainerHighest).forEach { (text, background) ->
                assertTrue("dark=$dark accent=$accent tone=$tone contrast=${contrast(text, background)}", contrast(text, background) >= 4.5f)
            }
        } } }
    }
}
