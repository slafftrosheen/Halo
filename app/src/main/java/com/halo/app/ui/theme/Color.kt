package com.halo.app.ui.theme

import androidx.compose.ui.graphics.Color

// Halo OS Colors based on SOT Image

// Legacy Halo Glass palette (kept for backward compatibility during migration)
val HaloBackground = Color(0xFF080A0B)
val HaloSurface = Color(0xFF141719)
val HaloCard = Color(0xFF1B1F20)

val HaloPrimary = Color(0xFF7DE2B1)
val HaloPrimaryStrong = Color(0xFF54D99A)
val HaloInformation = Color(0xFF4EA8FF)
val HaloWarning = Color(0xFFF0B86B)
val HaloDanger = Color(0xFFFF6B72)

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

// Core semantic states
val HaloGreen = Color(0xFF00C853) // Listening / Ready
val HaloBlue = Color(0xFF2979FF)  // Speaking / Action
val HaloPink = Color(0xFFFF4081)  // Focused Thinking
val HaloYellow = Color(0xFFFFD600) // Active Processing
val HaloOrange = Color(0xFFFF9100) // Connecting
val HaloGrey = Color(0xFF9E9E9E)   // Idle

// Light Theme Palette
val LightBackground = Color(0xFFF8F9FA) // Very light gray/off-white background
val LightSurface = Color(0xFFFFFFFF)    // Pure white for cards/surfaces
val LightSurfaceVariant = Color(0xFFE9ECEF)
val LightOnBackground = Color(0xFF111111) // Dark text for contrast
val LightOnSurface = Color(0xFF111111)
val LightOnSurfaceVariant = Color(0xFF6C757D)
val LightOutline = Color(0xFFDEE2E6)
val LightOutlineVariant = Color(0xFFE9ECEF)

// Dark Theme Palette
val DarkBackground = Color(0xFF0D1117)  // Deep graphite from Brand Identity
val DarkSurface = Color(0xFF161B22)     // Slightly lighter for cards
val DarkSurfaceVariant = Color(0xFF21262D)
val DarkOnBackground = Color(0xFFF5F7FA)
val DarkOnSurface = Color(0xFFF5F7FA)
val DarkOnSurfaceVariant = Color(0xFF8B949E)
val DarkOutline = Color(0xFF30363D)
val DarkOutlineVariant = Color(0xFF21262D)
