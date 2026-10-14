package com.halo.app.ui.theme

import androidx.compose.ui.graphics.Color

// Legacy Halo Glass palette remains while secondary screens migrate.
val HaloBackground = Color(0xFF14161A)
val HaloSurface = Color(0xFF1B1F24)
val HaloCard = Color(0xFF242830)

val HaloPrimary = Color(0xFF7DE2B1)
val HaloPrimaryStrong = Color(0xFF54D99A)
val HaloInformation = Color(0xFF4EA8FF)
val HaloWarning = Color(0xFFE8A84D)
val HaloDanger = Color(0xFFEB5A67)

val HaloTextPrimary = Color(0xFFF5F7F6)
val HaloTextSecondary = Color(0xFFA5ADA9)
val HaloTextMuted = Color(0xFF6B7471)

val HaloBorderSubtle = Color.White.copy(alpha = 0.20f)
val HaloBorder = Color.White.copy(alpha = 0.30f)
val HaloBorderStrong = Color.White.copy(alpha = 0.50f)
val HaloDivider = HaloBorderSubtle
val HaloSignalSurface = Color(0xFF101516)
val HaloGlassHighlight = Color.White.copy(alpha = 0.08f)
val HaloGlassShadow = Color.Black.copy(alpha = 0.45f)

// Workspace object/category colors. These are intentionally soft; they are not status colors.
val HaloPastelMint = Color(0xFFA6EBD8)
val HaloPastelBlue = Color(0xFFB9D9FF)
val HaloPastelLilac = Color(0xFFD5C8FF)
val HaloPastelPink = Color(0xFFF3C0D9)
val HaloPastelYellow = Color(0xFFF7DFA3)
val HaloPastelCoral = Color(0xFFF4BEA9)

// Stronger semantic colors are reserved for runtime meaning.
val HaloReady = Color(0xFF2FCB9A)
val HaloAction = Color(0xFF5B8DEF)
val HaloInfo = Color(0xFF4EA8FF)
val HaloSemanticWarning = HaloWarning
val HaloSemanticDanger = HaloDanger
val HaloIdle = Color(0xFF8E9199)

// Light cognitive workspace.
val LightBackground = Color(0xFFF7F8FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEDF0F8)
val LightOnBackground = Color(0xFF152044)
val LightOnSurface = Color(0xFF152044)
val LightOnSurfaceVariant = Color(0xFF536078)
val LightOutline = Color(0xFF7B879A)
val LightOutlineVariant = Color(0xFFDFE4EF)

// Dark companion theme: softer than pure black and intentionally non-cyberpunk.
val DarkBackground = Color(0xFF14161A)
val DarkSurface = Color(0xFF1B1F24)
val DarkSurfaceVariant = Color(0xFF242830)
val DarkOnBackground = Color(0xFFF5F7FA)
val DarkOnSurface = Color(0xFFF5F7FA)
val DarkOnSurfaceVariant = Color(0xFFBDC6D2)
val DarkOutline = Color(0xFF8893A3)
val DarkOutlineVariant = Color(0xFF363C46)
