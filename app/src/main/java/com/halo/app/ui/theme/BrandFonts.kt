package com.halo.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.halo.app.R

/**
 * Bundled and offline-only Halo brand fonts.
 * Sources: HaloWeb/assets/fonts, licensed under SIL OFL 1.1.
 *
 * Variable TTFs: Sora for headings, Atkinson Hyperlegible Next for reading,
 * Onest for Russian UI (the distributed Sora/Atkinson subsets lack Cyrillic).
 * See docs/15-brand/ANDROID-BRAND-PARITY.md for provenance and licensing.
 */
object HaloBrandFonts {
    val display: FontFamily = FontFamily(
        Font(R.font.sora_variable, FontWeight.Normal),
        Font(R.font.sora_variable, FontWeight.Medium),
        Font(R.font.sora_variable, FontWeight.SemiBold),
        Font(R.font.sora_variable, FontWeight.Bold),
    )
    val reading: FontFamily = FontFamily(
        Font(R.font.atkinson_hyperlegible_next_variable, FontWeight.Normal),
        Font(R.font.atkinson_hyperlegible_next_variable, FontWeight.Medium),
        Font(R.font.atkinson_hyperlegible_next_variable, FontWeight.Bold),
        Font(R.font.atkinson_hyperlegible_next_italic_variable, FontWeight.Normal, FontStyle.Italic),
    )
    val cyrillic: FontFamily = FontFamily(
        Font(R.font.onest_cyrillic_variable, FontWeight.Normal),
        Font(R.font.onest_cyrillic_variable, FontWeight.Medium),
        Font(R.font.onest_cyrillic_variable, FontWeight.SemiBold),
        Font(R.font.onest_cyrillic_variable, FontWeight.Bold),
    )
}
