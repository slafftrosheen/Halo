package com.halo.app.ui.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * Brand font slots. The offline-safe defaults are platform SansSerif until
 * licensed Sora and Atkinson Hyperlegible Next binaries are packaged.
 *
 * scripts/brand/install_fonts.py validates the local source files and replaces
 * this file with R.font mappings in the same operation that installs the TTFs.
 * Never rely on Google Fonts network requests during Halo startup.
 */
object HaloBrandFonts {
    val display: FontFamily = FontFamily.SansSerif
    val reading: FontFamily = FontFamily.SansSerif
}
