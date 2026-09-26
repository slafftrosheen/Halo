package com.halo.app.ui.presence

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.state.toSignalPresentation

/**
 * Halo's cognitive presence.
 *
 * Compatibility entry point for the shared Halo Signal Face renderer.
 */
@Composable
fun HaloPresence(
    state: HaloRuntimeState,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    reducedMotion: Boolean = false
) {
    val presentation = state.toSignalPresentation(reducedMotion = reducedMotion)
    HaloSignalFace(
        presentation = presentation,
        modifier = modifier,
        size = size
    )
}

enum class HaloSignalKind {
    HALO,
    USER,
    DEVICE,
    MEMORY,
    FILE,
    NOTE,
    ACTION,
    ERROR
}

/** Small identity mark for feeds, navigation accents and compact runtime state. */
@Composable
fun HaloSignalGlyph(
    kind: HaloSignalKind,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val glyph = when (kind) {
        HaloSignalKind.HALO -> HaloGlyph.HALO
        HaloSignalKind.USER -> HaloGlyph.HOME
        HaloSignalKind.DEVICE -> HaloGlyph.DEVICE
        HaloSignalKind.MEMORY -> HaloGlyph.MEMORY
        HaloSignalKind.FILE -> HaloGlyph.FILE
        HaloSignalKind.NOTE -> HaloGlyph.NOTE
        HaloSignalKind.ACTION -> HaloGlyph.ACTION
        HaloSignalKind.ERROR -> HaloGlyph.ERROR
    }
    HaloIcon(glyph = glyph, contentDescription = kind.name, modifier = modifier.size(size))
}
