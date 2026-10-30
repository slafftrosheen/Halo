package com.halo.app.ui.presence

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.*
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloGlyphRenderer
import com.halo.app.ui.theme.rememberReducedMotion

enum class HaloSignalFaceSize { HERO, STANDARD, COMPACT, MICRO }

/** Shared supplied artwork: compact/micro and reduced motion always use static vectors. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HaloSignalFace(
    presentation: HaloSignalPresentation,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    contentDescription: String = "Halo presence",
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    kids: Boolean = false,
    onClickLabel: String? = null,
    zone5: Boolean = false,
) {
    val localizedLabel = uiText(presentation.label)
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val systemReducedMotion = com.halo.app.ui.fx.rememberHaloReducedMotion()
    val highContrast = com.halo.app.ui.theme.rememberVisualPreferences().higherContrast
    val reduced = presentation.reducedMotion || systemReducedMotion || (zone5 && highContrast)
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val artwork = presentation.presenceArtwork()
    val foreground = lifecycleState.isAtLeast(if (zone5) Lifecycle.State.RESUMED else Lifecycle.State.STARTED)
    val zone5Resource = if (zone5) zone5OrbMotionResource(presentation, size.value, dark, !reduced && foreground) else null
    val policy = if (zone5) PresenceMotionPolicy(zone5Resource != null, loop = true)
        else artwork.motionPolicy(size.value, reduced, foreground)
    // Keep an eligible composition mounted as the Activity pauses. Discarding it on
    // ON_STOP would reset one-shot success/warning motion when Android resumes.
    val keepComposition = if (zone5) zone5Resource != null else artwork.canKeepLottieComposition(size.value, reduced)
    val prefix = if (kids) "halo_kids_orb_" else "halo_orb_"
    // Request Lottie only for foreground, standard-sized and motion-enabled faces.
    // Keep a same-state static fallback while composition loads or parsing fails.
    val assetPath = "halo/$prefix" + artwork.key + if (dark) "_dark.json" else "_light.json"
    val composition = if (keepComposition) {
        com.halo.app.ui.theme.rememberHaloThemedLottie(
            asset = if (zone5Resource == null) assetPath else null, rawRes = zone5Resource,
            accent = haloSignalInk(presentation.state, MaterialTheme.colorScheme))
    } else null
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f,
        tween(if (reduced) 0 else 90), label = "halo_touch")
    Box(modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }
        .semantics {
            this.contentDescription = contentDescription
            stateDescription = listOfNotNull(localizedLabel, presentation.detail).joinToString(", ")
        }.then(if (onClick != null || onLongClick != null) Modifier.combinedClickable(
            interactionSource = interactions, indication = null, role = Role.Button,
            onClickLabel = onClickLabel ?: uiText("Talk to Halo"), onClick = { onClick?.invoke() }, onLongClick = onLongClick,
        ) else Modifier), contentAlignment = Alignment.Center) {
        // Compact active states pulse; idle stays still without a permanent frame clock.
        when {
            policy.compact -> key(artwork) {
                CompactPresenceFrame(artwork, presentation)
            }
            composition == null -> ThemedPresenceFrame(presentation, Modifier.matchParentSize())
        }
        if (keepComposition) key(artwork, kids) {
            if (composition != null) {
                LottieAnimation(
                    composition = composition,
                    iterations = if (policy.loop) Int.MAX_VALUE else 1,
                    isPlaying = foreground,
                    restartOnPlay = false,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        val progressColor = MaterialTheme.colorScheme.primary
        if (artwork == PresenceArtwork.MODEL_LOADING && presentation.progress != null) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val inset = this.size.minDimension * .15f
                drawArc(progressColor, -90f, presentation.progress.coerceIn(0f, 1f) * 360f, false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(this.size.minDimension * .075f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
        if (size > 32.dp) {
            presentation.contextGlyph?.let { Badge(it, size * .28f, false,
                Modifier.align(Alignment.BottomEnd)) }
            presentation.conditionGlyph?.let { Badge(it, size * .28f, true,
                Modifier.align(Alignment.BottomStart), failure = presentation.state == HaloSignalState.ERROR) }
        }
    }
}

@Composable
private fun Badge(glyph: HaloGlyph, size: Dp, condition: Boolean, modifier: Modifier, failure: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    // Apricot is a semantic attention signal, not a selectable accent. On light
    // surfaces the deep shade is essential for non-text contrast (BrandAssets v0.7).
    val background = when {
        !condition -> colors.primaryContainer
        failure -> colors.errorContainer
        else -> colors.tertiaryContainer
    }
    val foreground = when {
        !condition -> colors.onPrimaryContainer
        failure -> colors.onErrorContainer
        else -> colors.onTertiaryContainer
    }
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        border = BorderStroke(size / 7f, colors.surface),
        color = background
    ) {
        Box(contentAlignment = Alignment.Center) {
            HaloGlyphRenderer(glyph, Modifier.size(size * .6f), tint = foreground)
        }
    }
}

/**
 * Full Lottie at >=80dp, vector motion for compact active states, static compact idle.
 * The actual runtime state determines the artwork: animation never fabricates activity.
 */
internal data class PresenceMotionPolicy(val animate: Boolean, val loop: Boolean, val compact: Boolean = false)

/** A mounted foreground-capable face keeps its composition, even while playback is paused. */
internal fun PresenceArtwork.canKeepLottieComposition(size: Float, reduced: Boolean): Boolean =
    motionPolicy(size, reduced, foreground = true).animate

internal fun PresenceArtwork.motionPolicy(size: Float, reduced: Boolean, foreground: Boolean): PresenceMotionPolicy {
    val enabled = !reduced && foreground && this in animatedArtwork
    return PresenceMotionPolicy(
        animate = enabled && size >= 80f,
        // The persistent full-size ring settles after its idle arrival motion.
        // Active runtime feedback can loop; idle must not hold a frame clock forever.
        loop = this != PresenceArtwork.IDLE && this != PresenceArtwork.SUCCESS && this != PresenceArtwork.WARNING,
        compact = enabled && size >= 40f && size < 80f && this in compactArtwork
    )
}

private val compactArtwork = setOf(
    PresenceArtwork.THINKING, PresenceArtwork.ACTING,
    PresenceArtwork.WAITING, PresenceArtwork.APPROVAL_REQUIRED
)

/** Preserve legibility at compact sizes: no miniature waveforms, glow or ripples. */
@Composable
private fun CompactPresenceFrame(artwork: PresenceArtwork, signal: HaloSignalPresentation) {
    val halfDuration = when (artwork) {
        PresenceArtwork.THINKING -> 1500
        else -> 1000
    }
    val transition = rememberInfiniteTransition(label = "halo_compact_${artwork.key}")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = halfDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_compact_pulse"
    )
    ThemedPresenceFrame(signal,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = .90f + .10f * pulse
        }
    )
}

internal val animatedArtwork = setOf(PresenceArtwork.IDLE, PresenceArtwork.LISTENING,
    PresenceArtwork.CAPTURING_VOICE, PresenceArtwork.CAPTURING_VISION, PresenceArtwork.THINKING,
    PresenceArtwork.ACTING, PresenceArtwork.SPEAKING, PresenceArtwork.STREAMING_TEXT,
    PresenceArtwork.WAITING, PresenceArtwork.APPROVAL_REQUIRED, PresenceArtwork.SUCCESS, PresenceArtwork.WARNING)

internal fun HaloSignalPresentation.presenceArtwork(): PresenceArtwork = artwork ?: when (state) {
    HaloSignalState.IDLE -> PresenceArtwork.IDLE
    HaloSignalState.LISTENING -> PresenceArtwork.LISTENING
    HaloSignalState.CAPTURING -> PresenceArtwork.CAPTURING_VOICE
    HaloSignalState.THINKING -> if (glyph == HaloGlyph.ACTION) PresenceArtwork.PLANNING else PresenceArtwork.THINKING
    HaloSignalState.ACTING -> if (glyph == HaloGlyph.PHONE) PresenceArtwork.ACTING_PHONE else PresenceArtwork.ACTING
    HaloSignalState.SPEAKING -> PresenceArtwork.SPEAKING
    HaloSignalState.WAITING -> when (glyph) {
        HaloGlyph.SEE -> PresenceArtwork.PREPARING_CONTEXT
        HaloGlyph.APPROVAL -> PresenceArtwork.APPROVAL_REQUIRED
        HaloGlyph.SECURITY -> PresenceArtwork.PERMISSION_MISSING
        HaloGlyph.DOWNLOAD -> PresenceArtwork.MODEL_LOADING
        else -> PresenceArtwork.WAITING
    }
    HaloSignalState.SUCCESS -> PresenceArtwork.SUCCESS
    HaloSignalState.WARNING -> PresenceArtwork.WARNING
    HaloSignalState.ERROR -> PresenceArtwork.ERROR
    HaloSignalState.OFFLINE -> PresenceArtwork.OFFLINE
    HaloSignalState.STOPPED -> PresenceArtwork.STOPPED
}

/** Exhaustive state mapping: callers cannot accidentally leave a state with the idle symbol. */
internal fun HaloSignalState.orbGlyph(): HaloGlyph = when (this) {
    HaloSignalState.IDLE -> HaloGlyph.HALO
    HaloSignalState.LISTENING -> HaloGlyph.LISTEN
    HaloSignalState.CAPTURING -> HaloGlyph.LISTEN
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
