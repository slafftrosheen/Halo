package com.halo.app.ui.presence

import androidx.compose.animation.core.animateFloatAsState
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
) {
    val localizedLabel = uiText(presentation.label)
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accent = MaterialTheme.colorScheme.primaryContainer
    val accentIndex = when (accent) {
        Color(0xFF8EC5FF) -> 1
        Color(0xFFC9B8FF) -> 2
        Color(0xFFFF9EB3) -> 3
        else -> 0
    }
    val systemReducedMotion by rememberReducedMotion()
    val reduced = presentation.reducedMotion || systemReducedMotion
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val artwork = presentation.presenceArtwork()
    val policy = artwork.motionPolicy(size.value, reduced,
        lifecycleState.isAtLeast(Lifecycle.State.STARTED))
    val prefix = if (kids) "halo_kids_orb_" else "halo_orb_"
    val composition = if (policy.animate) rememberLottieComposition(LottieCompositionSpec.Asset(
        "halo/$prefix" + artwork.key + if (dark) "_dark.json" else "_light.json")).value else null
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
            onClickLabel = uiText("Talk to Halo"), onClick = { onClick?.invoke() }, onLongClick = onLongClick,
        ) else Modifier), contentAlignment = Alignment.Center) {
        val staticResource = if (size <= 32.dp) artwork.microResource()
            else artwork.staticResource(dark, kids, accentIndex)
        // A static frame remains visible during asynchronous composition loading or parse failure.
        if (composition == null) Image(painterResource(staticResource), contentDescription = null, modifier = Modifier.matchParentSize())
        if (policy.animate) key(artwork, dark, kids) {
            val ringEnd = when (accentIndex) {
                1 -> Color(0xFFC9E3FF)
                2 -> Color(0xFFE2D9FF)
                3 -> Color(0xFFFFD0DB)
                else -> Color(0xFFB4E6D6)
            }
            val ringStart = when (accentIndex) {
                1 -> Color(0xFF6FB4FF)
                2 -> Color(0xFFA98EFF)
                3 -> Color(0xFFFF7E9C)
                else -> Color(0xFF0CF2BA)
            }
            val dynamic = rememberLottieDynamicProperties(
                rememberLottieDynamicProperty(LottieProperty.GRADIENT_COLOR,
                    arrayOf(ringStart.toArgb(), ringEnd.toArgb()), "ring", "**"))
            if (composition != null) {
                LottieAnimation(composition, iterations = if (policy.loop) LottieConstants.IterateForever else 1,
                    dynamicProperties = if (kids) null else dynamic,
                    modifier = Modifier.matchParentSize())
            }
        }
        if (artwork == PresenceArtwork.MODEL_LOADING && presentation.progress != null) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val inset = this.size.minDimension * .15f
                drawArc(accent, -90f, presentation.progress.coerceIn(0f, 1f) * 360f, false,
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
    Surface(modifier.size(size), shape = CircleShape,
        border = BorderStroke(size / 7f, Color(0xFF060330)),
        color = if (!condition) Color(0xFF1A1760) else if (failure) Color(0xFFD64545) else Color(0xFFFF9F6B)) {
        Box(contentAlignment = Alignment.Center) {
            HaloGlyphRenderer(glyph, Modifier.size(size * .6f),
                tint = if (condition) Color(0xFF060330) else Color(0xFF0CF2BA))
        }
    }
}

internal data class PresenceMotionPolicy(val animate: Boolean, val loop: Boolean)
internal fun PresenceArtwork.motionPolicy(size: Float, reduced: Boolean, foreground: Boolean) =
    PresenceMotionPolicy(size >= 80f && !reduced && foreground && this in animatedArtwork,
        this !in setOf(PresenceArtwork.SUCCESS, PresenceArtwork.WARNING))

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
