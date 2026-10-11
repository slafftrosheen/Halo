package com.halo.app.ui.theme

import androidx.compose.ui.graphics.Color

// Legacy Halo Glass palette remains while secondary screens migrate.
val HaloBackground = Color(0xFF080A0B)
val HaloSurface = Color(0xFF141719)
val HaloCard = Color(0xFF1B1F20)

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
val LightBackground = Color(0xFFF7F8F4)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEDF1EB)
val LightOnBackground = Color(0xFF182033)
val LightOnSurface = Color(0xFF182033)
val LightOnSurfaceVariant = Color(0xFF56635D)
val LightOutline = Color(0xFF77867E)
val LightOutlineVariant = Color(0xFFDDE5DE)

// Dark companion theme: softer than pure black and intentionally non-cyberpunk.
val DarkBackground = Color(0xFF131B19)
val DarkSurface = Color(0xFF1D2723)
val DarkSurfaceVariant = Color(0xFF29352F)
val DarkOnBackground = Color(0xFFF3F3F1)
val DarkOnSurface = Color(0xFFF3F3F1)
val DarkOnSurfaceVariant = Color(0xFFB3C3BA)
val DarkOutline = Color(0xFF84968B)
val DarkOutlineVariant = Color(0xFF35453C)
