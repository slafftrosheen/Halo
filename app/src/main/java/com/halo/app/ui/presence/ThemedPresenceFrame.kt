package com.halo.app.ui.presence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.halo.app.R
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
        Icon(painterResource(R.drawable.ic_halo_brand_halo), null, Modifier.fillMaxSize(), tint = ink)
        if (artwork != PresenceArtwork.IDLE && glyph != HaloGlyph.HALO) {
            Box(Modifier.size(maxWidth * .46f).clip(CircleShape).background(colors.surfaceContainerLow), contentAlignment = Alignment.Center) {
                HaloGlyphRenderer(glyph, Modifier.fillMaxSize(.75f), tint = colors.onSurface)
            }
        }
    }
}
