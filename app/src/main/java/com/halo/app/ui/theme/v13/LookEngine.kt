package com.halo.app.ui.theme.v13

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * halo 13.0 · looks that follow your wallpaper.
 *
 * "Match my wallpaper" takes ONE seed colour from the wallpaper (or the photo the person picked),
 * builds the accent + surface tints from it, then runs the contrast guard.
 * Meaning colours are locked and never re-tinted: mint = done, apricot = needs you, lilac = help, red = error.
 * Extends ui/theme/PalettePreferences.kt (HaloAccent, HaloTone) – adds HaloAccent.FROM_WALLPAPER.
 */
data class Rgb(val r: Int, val g: Int, val b: Int) {
    fun hex() = "#%02X%02X%02X".format(r, g, b)
    fun luminance(): Double {
        fun ch(c: Int) = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
    }
}

fun contrast(a: Rgb, b: Rgb): Double { val la = a.luminance(); val lb = b.luminance(); return (max(la, lb) + 0.05) / (min(la, lb) + 0.05) }

object MeaningColours {               // locked – FOUNDATIONS_13.md §5
    val DONE = Rgb(0x0C, 0xF2, 0xBA); val NEEDS_YOU = Rgb(0xFF, 0x9F, 0x6B)
    val HELP = Rgb(0xC9, 0xB8, 0xFF); val ERROR = Rgb(0xFF, 0x8F, 0xA3)
}

data class DerivedLook(val accent: Rgb, val onAccent: Rgb, val surfaceTint: Rgb, val adjusted: Boolean, val ratio: Double)

object LookEngine {
    const val TEXT_MIN = 4.5      // WCAG AA body text
    const val LARGE_MIN = 3.0     // icons, 18 pt+ text

    /** Picks the most "alive" colour: average of the most saturated 10 % of pixels (ARGB ints from a 64×64 thumbnail). */
    fun seed(pixels: IntArray): Rgb {
        require(pixels.isNotEmpty())
        val sorted = pixels.map { Rgb(it shr 16 and 255, it shr 8 and 255, it and 255) }
            .sortedByDescending { (max(it.r, max(it.g, it.b)) - min(it.r, min(it.g, it.b))) }
        val top = sorted.take(max(1, sorted.size / 10))
        return Rgb(top.sumOf { it.r } / top.size, top.sumOf { it.g } / top.size, top.sumOf { it.b } / top.size)
    }

    /** Moves the seed lighter (dark theme) or darker (light theme) until it passes against the surface. */
    fun derive(seed: Rgb, surface: Rgb, dark: Boolean): DerivedLook {
        val black = Rgb(0, 0, 0)
        val white = Rgb(255, 255, 255)
        val preferred = if (dark) white else black
        val target = if (contrast(preferred, surface) >= TEXT_MIN) preferred else if (dark) black else white
        var c = keepAwayFromMeaning(seed)
        for (step in 1..20) {
            if (contrast(c, surface) >= TEXT_MIN) break
            c = mix(c, target, .18)
        }
        if (contrast(c, surface) < TEXT_MIN) c = target
        val onAccent = if (contrast(black, c) >= contrast(white, c)) black else white
        return DerivedLook(c, onAccent, mix(surface, c, 0.08), c != seed, contrast(c, surface))
    }

    /** An accent must not look like "error" red – it would change what colours mean. */
    private fun keepAwayFromMeaning(c: Rgb): Rgb =
        if (c.r > 200 && c.g < 120 && c.b < 140) MeaningColours.NEEDS_YOU else c

    fun mix(a: Rgb, b: Rgb, t: Double) = Rgb(
        (a.r + (b.r - a.r) * t).toInt(), (a.g + (b.g - a.g) * t).toInt(), (a.b + (b.b - a.b) * t).toInt())
}
