package com.halo.app.ui.presence

import androidx.compose.ui.graphics.Color
import com.halo.app.ui.theme.HaloDanger
import com.halo.app.ui.theme.HaloNightInk
import com.halo.app.ui.theme.HaloCream
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelCoral
import com.halo.app.ui.theme.HaloPastelLilac
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.theme.HaloPastelYellow
import com.halo.app.ui.theme.HaloWarning

data class HaloSignalPalette(
    val halo: Color,
    val haloSecondary: Color,
    val display: Color,
    val displayHighlight: Color,
    val glyph: Color,
) {
    companion object {
        fun resolve(state: HaloSignalState, isDark: Boolean): HaloSignalPalette {
            val haloColor = when (state) {
                HaloSignalState.IDLE -> HaloPastelMint
                HaloSignalState.LISTENING -> HaloPastelBlue
                HaloSignalState.CAPTURING -> HaloPastelMint
                HaloSignalState.THINKING -> HaloPastelLilac
                HaloSignalState.ACTING -> HaloPastelCoral
                HaloSignalState.SPEAKING -> HaloPastelCoral
                HaloSignalState.WAITING -> HaloPastelYellow
                HaloSignalState.SUCCESS -> HaloPastelMint
                HaloSignalState.WARNING -> HaloWarning
                HaloSignalState.ERROR -> HaloDanger
                HaloSignalState.OFFLINE -> Color(0xFF8E8E93)
                HaloSignalState.STOPPED -> Color(0xFF636366)
            }

            val haloSecondaryColor = when (state) {
                HaloSignalState.IDLE -> HaloPastelMint
                HaloSignalState.LISTENING -> HaloPastelMint
                HaloSignalState.CAPTURING -> HaloPastelBlue
                HaloSignalState.THINKING -> HaloPastelBlue
                HaloSignalState.ACTING -> HaloPastelYellow
                HaloSignalState.SPEAKING -> HaloPastelYellow
                HaloSignalState.WAITING -> HaloPastelCoral
                HaloSignalState.SUCCESS -> HaloPastelBlue
                HaloSignalState.WARNING -> HaloPastelCoral
                HaloSignalState.ERROR -> HaloPastelCoral
                HaloSignalState.OFFLINE -> Color(0xFF636366)
                HaloSignalState.STOPPED -> Color(0xFF48484A)
            }

            // The inner OLED is always Night Ink, never a competing gray skin.
            // Brand mint and semantic state colours belong to the outer ring.
            val displayColor = HaloNightInk
            val displayHighlight = if (isDark) Color(0xFF1D1A4D) else Color(0xFF120F41)
            val glyphColor = HaloCream

            return HaloSignalPalette(
                halo = haloColor,
                haloSecondary = haloSecondaryColor,
                display = displayColor,
                displayHighlight = displayHighlight,
                glyph = glyphColor,
            )
        }
    }
}
