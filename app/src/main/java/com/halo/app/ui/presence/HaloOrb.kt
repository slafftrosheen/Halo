package com.halo.app.ui.presence

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.halo.app.ui.glass.material.HaloRuntimeState

@Immutable
data class HaloOrbPresentation(
    val phase: HaloRuntimeState = HaloRuntimeState.Idle,
    val label: String = "Ready",
    val source: String = "Phone",
    val inputLevel: Float = 0f,
    val failed: Boolean = false,
    val transition: Int = 0,
    val reducedMotion: Boolean = false
)

private val pearlCells = listOf(
    Rect(.24f,.22f,.42f,.40f), Rect(.47f,.18f,.65f,.36f),
    Rect(.68f,.34f,.80f,.48f), Rect(.19f,.46f,.37f,.63f),
    Rect(.42f,.43f,.62f,.61f), Rect(.64f,.56f,.78f,.73f), Rect(.35f,.67f,.54f,.81f)
)

/** One circular, matte computational pearl at hero and compact sizes. No idle clock. */
@Composable
fun HaloOrb(presentation: HaloOrbPresentation, modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val target = when (presentation.phase) {
        HaloRuntimeState.Listening, HaloRuntimeState.Capturing -> presentation.inputLevel.coerceIn(0f, 1f) * .06f
        HaloRuntimeState.Recognizing -> -.035f
        HaloRuntimeState.Reasoning, HaloRuntimeState.Planning -> .025f
        HaloRuntimeState.Executing -> if (presentation.transition % 2 == 0) .035f else -.025f
        HaloRuntimeState.Speaking -> .018f
        else -> 0f
    }
    val shift by animateFloatAsState(target, tween(if (presentation.reducedMotion) 0 else 280), label = "orbState")
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = when {
        presentation.failed -> MaterialTheme.colorScheme.error
        presentation.phase == HaloRuntimeState.Recovering -> Color(0xFFC58A3C)
        presentation.phase == HaloRuntimeState.Executing -> Color(0xFFEBA795)
        presentation.phase == HaloRuntimeState.Reasoning -> Color(0xFFC5B7DA)
        presentation.phase == HaloRuntimeState.Stopped -> surface
        else -> Color(0xFFB1D3C4)
    }
    Box(modifier.size(size).semantics {
        contentDescription = "Halo Orb"
        stateDescription = "${presentation.label}, input ${presentation.source}"
    }.drawWithCache {
        val diameter = this.size.minDimension
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val radius = diameter * .47f
        val silhouette = Path().apply { addOval(Rect(center, radius)) }
        val body = Brush.radialGradient(listOf(surface, surface.copy(alpha = .92f), ink.copy(alpha = .12f)),
            center = center - Offset(diameter * .14f, diameter * .18f), radius = diameter * .85f)
        val cells = pearlCells.map { Rect(it.left * diameter, it.top * diameter, it.right * diameter, it.bottom * diameter) }
        onDrawBehind {
            drawCircle(ink.copy(alpha = .05f), radius, center + Offset(0f, diameter * .018f))
            drawCircle(body, radius, center)
            clipPath(silhouette) {
                cells.forEachIndexed { index, rect ->
                    val offset = if (index % 2 == 0) shift else -shift * .65f
                    drawRoundRect(accent.copy(alpha = if (index == 4) .35f else .58f),
                        topLeft = rect.topLeft + Offset(offset * diameter, offset * diameter * .5f),
                        size = rect.size, cornerRadius = CornerRadius(diameter * .045f))
                }
                // A short light-break signature on the upper-right edge, never a progress ring.
                drawLine(if (presentation.failed) accent else ink.copy(alpha = .22f),
                    Offset(diameter * .83f, diameter * .16f), Offset(diameter * .88f, diameter * .22f),
                    strokeWidth = diameter * .022f, cap = StrokeCap.Round)
            }
        }
    })
}
