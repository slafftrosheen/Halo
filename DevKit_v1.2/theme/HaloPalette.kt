package com.halo.app.ui.theme

import androidx.compose.ui.graphics.Color

// Halo appearance tokens: Light/Dark x Natural/Warm/Cool x Mint/Blue/Lilac/Coral. Generated from the Halo brand kit.
enum class HaloTone { Natural, Warm, Cool }
enum class HaloAccent { Mint, Blue, Lilac, Coral }

data class HaloSurfaces(val background: Color, val surface: Color, val surface2: Color, val text: Color, val muted: Color)
data class HaloAccentColors(val fill: Color, val onFill: Color, val ink: Color, val ringStart: Color, val ringEnd: Color)
data class HaloSemantic(val attention: Color, val attentionInk: Color, val error: Color)

object HaloPalette {
    fun surfacesDark(tone: HaloTone) = when (tone) {
        HaloTone.Natural -> HaloSurfaces(Color(0xFF060330), Color(0xFF110E45), Color(0xFF1A1760), Color(0xFFFAFAF4), Color(0xFFB7B9D3))
        HaloTone.Warm -> HaloSurfaces(Color(0xFF160E1C), Color(0xFF22182A), Color(0xFF2E2238), Color(0xFFFBF6F0), Color(0xFFC9BCC4))
        HaloTone.Cool -> HaloSurfaces(Color(0xFF06142A), Color(0xFF0E2038), Color(0xFF172C48), Color(0xFFF4F8FC), Color(0xFFB3C2D4))
    }
    fun accentDark(accent: HaloAccent) = when (accent) {
        HaloAccent.Mint -> HaloAccentColors(Color(0xFF0CF2BA), Color(0xFF060330), Color(0xFF0CF2BA), Color(0xFF0CF2BA), Color(0xFFB4E6D6))
        HaloAccent.Blue -> HaloAccentColors(Color(0xFF8EC5FF), Color(0xFF06142A), Color(0xFF8EC5FF), Color(0xFF6FB4FF), Color(0xFFC9E3FF))
        HaloAccent.Lilac -> HaloAccentColors(Color(0xFFC9B8FF), Color(0xFF160E3A), Color(0xFFC9B8FF), Color(0xFFA98EFF), Color(0xFFE2D9FF))
        HaloAccent.Coral -> HaloAccentColors(Color(0xFFFF9EB3), Color(0xFF2A0A14), Color(0xFFFF9EB3), Color(0xFFFF7E9C), Color(0xFFFFD0DB))
    }
    fun surfacesLight(tone: HaloTone) = when (tone) {
        HaloTone.Natural -> HaloSurfaces(Color(0xFFFAFAF4), Color(0xFFFFFFFF), Color(0xFFEFF0E8), Color(0xFF060330), Color(0xFF4A4F63))
        HaloTone.Warm -> HaloSurfaces(Color(0xFFFFF5EC), Color(0xFFFFFFFF), Color(0xFFF7E9DD), Color(0xFF1A0F14), Color(0xFF5E4F55))
        HaloTone.Cool -> HaloSurfaces(Color(0xFFF3F6FA), Color(0xFFFFFFFF), Color(0xFFE5ECF4), Color(0xFF06142A), Color(0xFF46566B))
    }
    fun accentLight(accent: HaloAccent) = when (accent) {
        HaloAccent.Mint -> HaloAccentColors(Color(0xFF0CF2BA), Color(0xFF060330), Color(0xFF047A5E), Color(0xFF0CF2BA), Color(0xFFB4E6D6))
        HaloAccent.Blue -> HaloAccentColors(Color(0xFF8EC5FF), Color(0xFF06142A), Color(0xFF1F5FBF), Color(0xFF6FB4FF), Color(0xFFC9E3FF))
        HaloAccent.Lilac -> HaloAccentColors(Color(0xFFC9B8FF), Color(0xFF160E3A), Color(0xFF6446D6), Color(0xFFA98EFF), Color(0xFFE2D9FF))
        HaloAccent.Coral -> HaloAccentColors(Color(0xFFFF9EB3), Color(0xFF2A0A14), Color(0xFFB8325A), Color(0xFFFF7E9C), Color(0xFFFFD0DB))
    }
    val semanticDark = HaloSemantic(Color(0xFFFF9F6B), Color(0xFFFF9F6B), Color(0xFFFF7A7A))
    val semanticLight = HaloSemantic(Color(0xFFD9692A), Color(0xFFA84B12), Color(0xFFC53030))
    // Brand marks (logo, launcher) always use Halo Mint, never the accent.
    val brandMint = Color(0xFF0CF2BA)
    val nightInk = Color(0xFF060330)
    val cream = Color(0xFFFAFAF4)
}
