package com.halo.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import com.halo.app.R

/**
 * Bundled and offline-only Halo brand fonts.
 * Sources: HaloWeb/assets/fonts, licensed under SIL OFL 1.1.
 *
 * Variable TTFs: Sora for headings, Atkinson Hyperlegible Next for reading,
 * Onest for Russian UI (the distributed Sora/Atkinson subsets lack Cyrillic).
 * See docs/15-brand/ANDROID-BRAND-PARITY.md for provenance and licensing.
 */
@OptIn(ExperimentalTextApi::class)
object HaloBrandFonts {
    // The resource Font overload without variationSettings matches the weight
    // metadata but leaves a variable font's drawing axis at its default value.
    // Set wght explicitly so Sora/Atkinson/Onest actually draw the selected face.
    private fun variableFont(resource: Int, weight: FontWeight, style: FontStyle = FontStyle.Normal) =
        Font(resource, weight, style,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    val display: FontFamily = FontFamily(
        variableFont(R.font.sora_variable, FontWeight.Normal),
        variableFont(R.font.sora_variable, FontWeight.Medium),
        variableFont(R.font.sora_variable, FontWeight.SemiBold),
        variableFont(R.font.sora_variable, FontWeight.Bold),
    )
    val reading: FontFamily = FontFamily(
        variableFont(R.font.atkinson_hyperlegible_next_variable, FontWeight.Normal),
        variableFont(R.font.atkinson_hyperlegible_next_variable, FontWeight.Medium),
        variableFont(R.font.atkinson_hyperlegible_next_variable, FontWeight.SemiBold),
        variableFont(R.font.atkinson_hyperlegible_next_variable, FontWeight.Bold),
        variableFont(R.font.atkinson_hyperlegible_next_italic_variable, FontWeight.Normal, FontStyle.Italic),
    )
    val cyrillic: FontFamily = FontFamily(
        variableFont(R.font.onest_cyrillic_variable, FontWeight.Normal),
        variableFont(R.font.onest_cyrillic_variable, FontWeight.Medium),
        variableFont(R.font.onest_cyrillic_variable, FontWeight.SemiBold),
        variableFont(R.font.onest_cyrillic_variable, FontWeight.Bold),
    )
}
