package com.halo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = HaloBrandFonts.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.6).sp
    ),
    displayMedium = TextStyle(
        fontFamily = HaloBrandFonts.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.35).sp
    ),
    displaySmall = TextStyle(
        fontFamily = HaloBrandFonts.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = HaloBrandFonts.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = HaloBrandFonts.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.15).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = HaloBrandFonts.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = HaloBrandFonts.display,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 27.sp
    ),
    titleMedium = TextStyle(
        fontFamily = HaloBrandFonts.display,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = HaloBrandFonts.display,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = HaloBrandFonts.reading,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = HaloBrandFonts.reading,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodySmall = TextStyle(
        fontFamily = HaloBrandFonts.reading,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = HaloBrandFonts.reading,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = HaloBrandFonts.reading,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = HaloBrandFonts.reading,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp
    )
)


/**
 * HaloWeb uses a Cyrillic-specific Onest face because the shipped Sora and
 * Atkinson subsets do not contain Russian glyphs. Compose cannot apply a
 * CSS unicode-range, so Russian app-language screens use Onest throughout
 * their UI typography; user content is never rewritten or transliterated.
 */
private val RussianTypography = Typography.copy(
    displayLarge = Typography.displayLarge.copy(fontFamily = HaloBrandFonts.cyrillic),
    displayMedium = Typography.displayMedium.copy(fontFamily = HaloBrandFonts.cyrillic),
    displaySmall = Typography.displaySmall.copy(fontFamily = HaloBrandFonts.cyrillic),
    headlineLarge = Typography.headlineLarge.copy(fontFamily = HaloBrandFonts.cyrillic),
    headlineMedium = Typography.headlineMedium.copy(fontFamily = HaloBrandFonts.cyrillic),
    headlineSmall = Typography.headlineSmall.copy(fontFamily = HaloBrandFonts.cyrillic),
    titleLarge = Typography.titleLarge.copy(fontFamily = HaloBrandFonts.cyrillic),
    titleMedium = Typography.titleMedium.copy(fontFamily = HaloBrandFonts.cyrillic),
    titleSmall = Typography.titleSmall.copy(fontFamily = HaloBrandFonts.cyrillic),
    bodyLarge = Typography.bodyLarge.copy(fontFamily = HaloBrandFonts.cyrillic),
    bodyMedium = Typography.bodyMedium.copy(fontFamily = HaloBrandFonts.cyrillic),
    bodySmall = Typography.bodySmall.copy(fontFamily = HaloBrandFonts.cyrillic),
    labelLarge = Typography.labelLarge.copy(fontFamily = HaloBrandFonts.cyrillic),
    labelMedium = Typography.labelMedium.copy(fontFamily = HaloBrandFonts.cyrillic),
    labelSmall = Typography.labelSmall.copy(fontFamily = HaloBrandFonts.cyrillic),
)

internal fun haloTypographyForLanguage(language: String) =
    if (language.equals("ru", ignoreCase = true)) RussianTypography else Typography
