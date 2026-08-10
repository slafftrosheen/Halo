package com.halo.app.ui.glass.material

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.LinearGradient
import androidx.compose.ui.unit.Dp

object HaloGlassColors {
    // Base (near-black)
    val Background = Color(0xFF080A0B)
    val BackgroundSecondary = Color(0xFF0D1012)

    // Glass containers (opaque equivalents for non-blur fallback)
    val Glass = Color(0xFF141719)
    val GlassElevated = Color(0xFF1B1F20)
    val GlassStrong = Color(0xFF222729)

    // Text
    val TextPrimary = Color(0xFFF5F7F6)
    val TextSecondary = Color(0xFFA5ADA9)
    val TextMuted = Color(0xFF6B7471)

    // Accent - Halo Green/Cyan Hybrid
    val Accent = Color(0xFF7DE2B1)        // Primary accent
    val AccentStrong = Color(0xFF54D99A)  // Pressed/active
    val AccentMuted = Color(0x337DE2B1)   // Backgrounds (20% accent)
    val AccentContainer = Color(0x1A7DE2B1) // 10% accent

    // Semantic
    val Success = Color(0xFF7DE2B1)
    val Warning = Color(0xFFF0B86B)
    val Danger = Color(0xFFFF6B72)
    val Info = Color(0xFF4EA8FF)

    // Borders/Dividers
    val BorderSubtle = Color(0x33FFFFFF)   // 20%
    val BorderStandard = Color(0x4DFFFFFF) // 30%
    val BorderStrong = Color(0x80FFFFFF)   // 50%

    // Highlights
    val HighlightTop = Brush.linearGradient(
        colors = listOf(
            Color(0x1FFFFFFF), // 12% white at top
            Color(0x00FFFFFF)  // Transparent at bottom
        )
    )
    
    val HighlightBottom = Brush.linearGradient(
        colors = listOf(
            Color(0x00000000), // Transparent at top
            Color(0x0A000000)  // 4% black at bottom
        )
    )
}