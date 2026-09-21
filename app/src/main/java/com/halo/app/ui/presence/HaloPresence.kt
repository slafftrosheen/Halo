package com.halo.app.ui.presence

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.theme.HaloDanger
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelCoral
import com.halo.app.ui.theme.HaloPastelLilac
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.theme.HaloPastelPink
import com.halo.app.ui.theme.HaloPastelYellow
import com.halo.app.ui.theme.HaloWarning
import kotlin.math.min

/**
 * Halo's cognitive presence.
 *
 * Compatibility entry point for the shared Halo Orb renderer.
 * It is an asymmetric set of soft spatial cells whose posture reflects real runtime state.
 * Motion only happens while transitioning between states; there is no decorative idle loop.
 */
@Composable
fun HaloPresence(
    state: HaloRuntimeState,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    reducedMotion: Boolean = false
) {
    HaloOrb(HaloOrbPresentation(phase = state, label = state.name, reducedMotion = reducedMotion), modifier, size)

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
    val palette = when (kind) {
        HaloSignalKind.HALO -> listOf(HaloPastelMint, HaloPastelBlue, HaloPastelLilac)
        HaloSignalKind.USER -> listOf(HaloPastelBlue, HaloPastelMint, HaloPastelBlue)
        HaloSignalKind.DEVICE -> listOf(HaloPastelLilac, HaloPastelBlue, HaloPastelMint)
        HaloSignalKind.MEMORY -> listOf(HaloPastelPink, HaloPastelLilac, HaloPastelMint)
        HaloSignalKind.FILE -> listOf(HaloPastelBlue, HaloPastelLilac, HaloPastelMint)
        HaloSignalKind.NOTE -> listOf(HaloPastelYellow, HaloPastelPink, HaloPastelBlue)
        HaloSignalKind.ACTION -> listOf(HaloPastelCoral, HaloPastelMint, HaloPastelBlue)
        HaloSignalKind.ERROR -> listOf(HaloDanger, HaloPastelCoral, HaloDanger)
    }

    Canvas(modifier = modifier.size(size)) {
        val extent = min(this.size.width, this.size.height)
        val cell = extent * 0.28f
        val radius = CornerRadius(cell * 0.28f)
        val points = listOf(
            Offset(extent * 0.18f, extent * 0.38f),
            Offset(extent * 0.45f, extent * 0.18f),
            Offset(extent * 0.53f, extent * 0.52f)
        )
        points.forEachIndexed { index, point ->
            drawRoundRect(
                color = palette[index],
                topLeft = point,
                size = Size(cell, cell),
                cornerRadius = radius
            )
        }
    }
}

private enum class CellRole(val color: Color) {
    BLUE(HaloPastelBlue),
    MINT(HaloPastelMint),
    LILAC(HaloPastelLilac),
    PINK(HaloPastelPink),
    YELLOW(HaloPastelYellow),
    CORAL(HaloPastelCoral)
}

private data class PresenceCell(
    val x: Float,
    val y: Float,
    val size: Float,
    val role: CellRole,
    val alpha: Float
)

private data class PresenceMarker(val x: Float, val y: Float, val role: CellRole)

private val cells = listOf(
    PresenceCell(0.00f, 0.00f, 1.10f, CellRole.LILAC, 0.92f),
    PresenceCell(-0.82f, -0.18f, 0.72f, CellRole.BLUE, 0.80f),
    PresenceCell(0.72f, -0.62f, 0.58f, CellRole.MINT, 0.78f),
    PresenceCell(0.94f, 0.42f, 0.78f, CellRole.BLUE, 0.72f),
    PresenceCell(-0.54f, 0.86f, 0.66f, CellRole.PINK, 0.70f),
    PresenceCell(-1.08f, 0.72f, 0.38f, CellRole.YELLOW, 0.76f),
    PresenceCell(0.18f, 1.12f, 0.46f, CellRole.MINT, 0.72f),
    PresenceCell(1.24f, -0.18f, 0.34f, CellRole.CORAL, 0.70f),
    PresenceCell(-0.18f, -1.18f, 0.38f, CellRole.LILAC, 0.68f)
)

private val markers = listOf(
    PresenceMarker(-1.48f, -0.64f, CellRole.BLUE),
    PresenceMarker(1.54f, 0.68f, CellRole.PINK),
    PresenceMarker(0.92f, -1.38f, CellRole.YELLOW),
    PresenceMarker(-1.30f, 1.18f, CellRole.MINT)
)

private data class PresenceProfile(
    val spread: Float,
    val vertical: Float,
    val rotation: Float,
    val coreScale: Float,
    val opacity: Float,
    val recovery: Boolean = false,
    val stopped: Boolean = false
)

private fun HaloRuntimeState.presenceProfile(): PresenceProfile = when (this) {
    HaloRuntimeState.Idle -> PresenceProfile(0.88f, 0.92f, -4f, 1.00f, 0.86f)
    HaloRuntimeState.Listening -> PresenceProfile(1.04f, 1.16f, 2f, 1.04f, 0.96f)
    HaloRuntimeState.Wakeword -> PresenceProfile(1.10f, 1.20f, 5f, 1.07f, 1.00f)
    HaloRuntimeState.Capturing -> PresenceProfile(1.14f, 1.30f, -3f, 1.08f, 1.00f)
    HaloRuntimeState.Recognizing -> PresenceProfile(0.78f, 0.88f, 8f, 0.95f, 0.92f)
    HaloRuntimeState.Reasoning -> PresenceProfile(0.92f, 1.04f, -12f, 1.03f, 0.98f)
    HaloRuntimeState.Planning -> PresenceProfile(0.98f, 0.96f, 14f, 1.02f, 0.98f)
    HaloRuntimeState.Executing -> PresenceProfile(1.08f, 0.90f, 18f, 1.08f, 1.00f)
    HaloRuntimeState.Speaking -> PresenceProfile(1.18f, 1.08f, -6f, 1.10f, 1.00f)
    HaloRuntimeState.Recovering -> PresenceProfile(0.84f, 0.92f, 6f, 0.96f, 0.92f, recovery = true)
    HaloRuntimeState.Stopped -> PresenceProfile(0.70f, 0.82f, 0f, 0.90f, 0.54f, stopped = true)
}
