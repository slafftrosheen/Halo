package com.halo.app.ui.theme

import androidx.compose.ui.graphics.Color

// Canonical Halo Brand Book 1.0 tokens, shared with HaloWeb/css/tokens.css.
// Night Ink, Mint and Cream are identity colours. Semantic errors remain separate.
val HaloMint = Color(0xFF0CF2BA)
val HaloNightInk = Color(0xFF060330)
val HaloDeepMint = Color(0xFF047A5E)
val HaloCream = Color(0xFFFAFAF4)
val HaloSlate = Color(0xFF4A4F63)
val HaloSunriseApricot = Color(0xFFFF9F6B)
val HaloMintTint = Color(0xFFB4E6D6)

// Legacy Halo Glass palette remains while secondary screens migrate.
val HaloBackground = HaloNightInk
val HaloSurface = Color(0xFF110E45)
val HaloCard = Color(0xFF1A1760)

val HaloPrimary = HaloMint
val HaloPrimaryStrong = HaloMint
val HaloInformation = Color(0xFFB6DFE2)
val HaloWarning = Color(0xFFFF9F6B)
val HaloDanger = Color(0xFFEB5A67)

val HaloTextPrimary = HaloCream
val HaloTextSecondary = Color(0xFFB7B9D3)
val HaloTextMuted = Color(0xFFADB2C8)

val HaloBorderSubtle = Color.White.copy(alpha = 0.20f)
val HaloBorder = Color.White.copy(alpha = 0.30f)
val HaloBorderStrong = Color.White.copy(alpha = 0.50f)
val HaloDivider = HaloBorderSubtle
val HaloSignalSurface = HaloNightInk
val HaloGlassHighlight = Color.White.copy(alpha = 0.08f)
val HaloGlassShadow = Color.Black.copy(alpha = 0.45f)

// Workspace object/category colors. These are intentionally soft; they are not status colors.
val HaloPastelMint = HaloMintTint
val HaloPastelBlue = Color(0xFFB6DFE2)
val HaloPastelLilac = Color(0xFFD7E2DD)
val HaloPastelPink = Color(0xFFF3C0D9)
val HaloPastelYellow = Color(0xFFF3DBC4)
val HaloPastelCoral = HaloSunriseApricot

// Stronger semantic colors are reserved for runtime meaning.
val HaloReady = HaloMint
val HaloAction = Color(0xFFB6DFE2)
val HaloInfo = Color(0xFF4EA8FF)
val HaloSemanticWarning = HaloWarning
val HaloSemanticDanger = HaloDanger
val HaloIdle = HaloSlate

// Light cognitive workspace.
val LightBackground = HaloCream
val LightSurface = Color.White
val LightSurfaceVariant = Color(0xFFF0F3EF)
val LightOnBackground = HaloNightInk
val LightOnSurface = HaloNightInk
val LightOnSurfaceVariant = Color(0xFF454961)
val LightOutline = Color(0xFFBCC9C4)
val LightOutlineVariant = Color(0xFFDDE2DD)

// Dark companion theme: softer than pure black and intentionally non-cyberpunk.
val DarkBackground = HaloNightInk
val DarkSurface = Color(0xFF110E45)
val DarkSurfaceVariant = Color(0xFF1A1760)
val DarkOnBackground = HaloCream
val DarkOnSurface = HaloCream
val DarkOnSurfaceVariant = Color(0xFFB7B9D3)
val DarkOutline = Color(0xFF514E75)
val DarkOutlineVariant = Color(0xFF343159)
