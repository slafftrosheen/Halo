package com.halo.app.ui.presence

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloGlyphRenderer

internal fun haloSignalInk(state: HaloSignalState, colors: ColorScheme): Color = when (state) {
        HaloSignalState.ERROR -> colors.error
        HaloSignalState.WARNING, HaloSignalState.WAITING -> colors.tertiary
        HaloSignalState.OFFLINE, HaloSignalState.STOPPED -> colors.onSurfaceVariant
        else -> colors.primary
}

@Composable
internal fun ThemedPresenceFrame(signal: HaloSignalPresentation, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val artwork = signal.presenceArtwork()
    val ink = haloSignalInk(signal.state, colors)
    val highContrast = com.halo.app.ui.theme.rememberVisualPreferences().higherContrast
    val glyph = if (signal.glyph != HaloGlyph.HALO) signal.glyph else when (artwork) {
        PresenceArtwork.CAPTURING_VOICE, PresenceArtwork.LISTENING -> HaloGlyph.LISTEN
        PresenceArtwork.THINKING, PresenceArtwork.PLANNING -> HaloGlyph.THINK
        PresenceArtwork.SPEAKING -> HaloGlyph.SPEAK
        PresenceArtwork.SUCCESS -> HaloGlyph.SUCCESS
        PresenceArtwork.ERROR -> HaloGlyph.ERROR
        PresenceArtwork.WARNING -> HaloGlyph.WARNING
        PresenceArtwork.APPROVAL_REQUIRED -> HaloGlyph.APPROVAL
        PresenceArtwork.OFFLINE -> HaloGlyph.OFFLINE
        PresenceArtwork.STOPPED -> HaloGlyph.STOP
        else -> signal.glyph
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Match the legacy 240-unit Lottie's 168-unit ring and 18-unit rounded stroke.
            // The settled glow uses the same three opacity stops; it holds still under reduced motion.
            val side = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            if (!highContrast) drawCircle(Brush.radialGradient(
                0f to ink.copy(alpha = .154f), .55f to ink.copy(alpha = .121f), 1f to Color.Transparent,
                center = center, radius = side * (118f / 240f)), radius = side * (118f / 240f), center = center)
            val radius = side * (84f / 240f)
            drawArc(ink, -32f, 316f, false, topLeft = center - Offset(radius, radius),
                size = Size(radius * 2f, radius * 2f), style = Stroke(side * (18f / 240f), cap = StrokeCap.Round))
        }
        if (artwork != PresenceArtwork.IDLE && glyph != HaloGlyph.HALO) {
            Box(Modifier.size(maxWidth * .46f).clip(CircleShape).background(colors.surfaceContainerLow), contentAlignment = Alignment.Center) {
                HaloGlyphRenderer(glyph, Modifier.fillMaxSize(.75f), tint = colors.onSurface)
            }
        }
    }
}
