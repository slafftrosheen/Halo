package com.halo.app.ui.presence

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import com.halo.app.language.uiText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloGlyphRenderer
import com.halo.app.ui.theme.rememberReducedMotion

enum class HaloSignalFaceSize { HERO, STANDARD, COMPACT, MICRO }

/** Motion is read only by drawing/layer phases, never by the surrounding screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HaloSignalFace(
    presentation: HaloSignalPresentation,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    contentDescription: String = "Halo presence",
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val localizedLabel = uiText(presentation.label)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = MaterialTheme.colorScheme.primary
    val palette = remember(presentation.state, dark, accent) {
        val resolved = HaloSignalPalette.resolve(presentation.state, dark)
        if (presentation.state in setOf(HaloSignalState.IDLE, HaloSignalState.LISTENING,
                HaloSignalState.CAPTURING, HaloSignalState.THINKING, HaloSignalState.ACTING, HaloSignalState.SPEAKING))
            resolved.copy(halo = accent, haloSecondary = accent) else resolved
    }
    val systemReducedMotion by rememberReducedMotion()
    val reducedMotion = presentation.reducedMotion || systemReducedMotion
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(if (reducedMotion) 0 else 90), label = "halo_touch",
    )
    val moving = !reducedMotion && foreground && size >= 72.dp &&
        presentation.state !in setOf(HaloSignalState.OFFLINE, HaloSignalState.STOPPED, HaloSignalState.ERROR, HaloSignalState.WARNING)
    val still = remember { mutableStateOf(0f) }
    val phase: State<Float> = if (moving) {
        rememberInfiniteTransition(label = "halo_breath").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
            label = "halo_phase",
        )
    } else still
    val activity = presentation.activityLevel.coerceIn(0f, 1f)
    Box(
        modifier = modifier.size(size)
            .graphicsLayer { scaleX = pressScale.value; scaleY = pressScale.value }
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = listOfNotNull(localizedLabel, presentation.detail).joinToString(", ")
            }
            .then(if (onClick != null || onLongClick != null) Modifier.combinedClickable(
                interactionSource = interactions, indication = null, role = Role.Button,
                onClickLabel = "Interact with Halo", onClick = { onClick?.invoke() },
                onLongClick = onLongClick,
            ) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().drawWithCache {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension * 0.38f
            val ring = this.size.minDimension * 0.038f
            onDrawBehind {
                val t = phase.value
                val breath = if (moving) (kotlin.math.sin(t * Math.PI * 2).toFloat() + 1f) * 0.5f else 0.5f
                val r = radius * (1f + if (reducedMotion) 0f else breath * 0.025f + activity * 0.035f)
                // A broken orbital ring and a satellite are Halo's signature.
                // Flat surfaces keep the state glyph readable at every size.
                drawCircle(palette.halo.copy(alpha = 0.08f), r + ring * 2f, c)
                drawCircle(palette.display, r - ring, c)
                drawArc(palette.halo, 28f, 304f, false,
                    c - Offset(r, r), Size(r * 2, r * 2),
                    style = Stroke(ring, cap = StrokeCap.Round))
                drawCircle(palette.halo, ring * 0.85f, c + Offset(r, 0f))
                drawCircle(palette.halo.copy(alpha = 0.22f), r * 0.79f, c,
                    style = Stroke(1.dp.toPx()))
                if (presentation.state in setOf(HaloSignalState.THINKING, HaloSignalState.ACTING, HaloSignalState.CAPTURING)) {
                    rotate(t * 360f, c) {
                        drawArc(palette.glyph.copy(alpha = 0.8f), -90f, 48f, false,
                            c - Offset(r, r), Size(r * 2, r * 2), style = Stroke(ring * 0.55f, cap = StrokeCap.Round))
                    }
                }
                presentation.progress?.let {
                    drawArc(palette.glyph, -90f, it.coerceIn(0f, 1f) * 360f, false,
                        c - Offset(r, r), Size(r * 2, r * 2), style = Stroke(ring, cap = StrokeCap.Round))
                }

            }
        })
        if (size >= 16.dp) {
            Crossfade(presentation.orbGlyph(), animationSpec = tween(if (reducedMotion) 0 else 160), label = "halo_state") { glyph ->
                HaloGlyphRenderer(glyph = glyph, modifier = Modifier.size(size * 0.38f), tint = palette.glyph)
            }
        }
    }
}

/** Exhaustive state mapping: callers cannot accidentally leave a state with the idle symbol. */
internal fun HaloSignalState.orbGlyph(): HaloGlyph = when (this) {
    HaloSignalState.IDLE -> HaloGlyph.HALO
    HaloSignalState.LISTENING -> HaloGlyph.LISTEN
    HaloSignalState.CAPTURING -> HaloGlyph.CAMERA
    HaloSignalState.THINKING -> HaloGlyph.THINK
    HaloSignalState.ACTING -> HaloGlyph.TOOL
    HaloSignalState.SPEAKING -> HaloGlyph.SPEAK
    HaloSignalState.WAITING -> HaloGlyph.APPROVAL
    HaloSignalState.SUCCESS -> HaloGlyph.SUCCESS
    HaloSignalState.WARNING -> HaloGlyph.WARNING
    HaloSignalState.ERROR -> HaloGlyph.ERROR
    HaloSignalState.OFFLINE -> HaloGlyph.OFFLINE
    HaloSignalState.STOPPED -> HaloGlyph.STOP
}

internal fun HaloSignalPresentation.orbGlyph(): HaloGlyph =
    if ((state == HaloSignalState.THINKING && glyph in setOf(HaloGlyph.LISTEN, HaloGlyph.ACTION)) ||
        (state == HaloSignalState.WAITING && glyph in setOf(HaloGlyph.SEE, HaloGlyph.QUESTION, HaloGlyph.APPROVAL, HaloGlyph.DEVICE, HaloGlyph.STORAGE, HaloGlyph.PAUSE))) glyph
    else state.orbGlyph()
