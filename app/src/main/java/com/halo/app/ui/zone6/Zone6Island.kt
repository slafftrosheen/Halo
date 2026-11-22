package com.halo.app.ui.zone6

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.*
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.presence.*
import com.halo.app.ui.theme.HaloLookMotion
import com.halo.app.ui.theme.rememberVisualPreferences

@Composable
internal fun Zone6LiveIsland(signal: HaloSignalPresentation, interaction: HaloOrbInteraction, reducedMotion: Boolean,
                            onPrimary: () -> Unit, onStop: () -> Unit, more: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val graph = com.halo.app.di.ManualDependencyGraph
    val kids by graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val continuity by graph.interactionContinuity.state.collectAsStateWithLifecycle()
    val delivered by graph.wearEndpointBridge.delivered.collectAsStateWithLifecycle()
    val audio by graph.haloSpeechOutputCoordinator.session.state.collectAsStateWithLifecycle()
    val speaking = audio as? com.halo.app.audio.HaloAudioSessionState.Speaking
    val wearable = (speaking?.target as? com.halo.sdk.runtime.SpeechOutputTarget.Device)
        ?.takeIf { it.kind == com.halo.sdk.runtime.AudioDeviceKind.WEARABLE &&
            speaking?.route?.mode == com.halo.sdk.runtime.SpeechRouteMode.MANAGED_DEVICE }
    val routeDetail = if (kids) null else when {
        signal.state == HaloSignalState.SPEAKING && wearable != null ->
            stringResource(R.string.follow_me_speaking_on, wearable.displayName ?: stringResource(R.string.follow_me_glasses))
        signal.state == HaloSignalState.SUCCESS ->
            delivered?.takeIf { it.interactionId == continuity.interactionId }?.let { stringResource(R.string.follow_me_delivered_to, it.label) }
        else -> null
    }
    val tone = haloSignalInk(signal.state, colors)
    val visual = rememberVisualPreferences()
    val foreground = com.halo.app.ui.fx.rememberHaloMotionForeground()
    val movement = foreground && !reducedMotion && visual.motionLevel != HaloLookMotion.STILL
    val duration = when {
        !movement -> 0
        visual.motionLevel == HaloLookMotion.FULL -> 550
        else -> 240
    }
    val lift = remember { Animatable(if (movement) 0f else 1f) }
    LaunchedEffect(movement) {
        if (!movement) lift.snapTo(1f)
        else if (lift.value < 1f) lift.animateTo(1f, tween(duration, easing = FastOutSlowInEasing))
    }
    val inset = if (visual.motionLevel == HaloLookMotion.FULL) Zone6DockTokens.inset else 6.dp
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = inset)
            .graphicsLayer {
                val progress = if (movement) lift.value else 1f
                alpha = .35f + .65f * progress
                translationY = (1f - progress) * 18.dp.toPx()
                scaleX = .95f + .05f * progress
                scaleY = .95f + .05f * progress
            }.semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(26.dp), color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, tone.copy(alpha = if (visual.higherContrast) .94f else .50f)),
        shadowElevation = if (visual.higherContrast) 0.dp else 4.dp,
    ) {
        Box {
            if (movement && !visual.higherContrast && visual.edgeLight) {
                Zone6IslandEdge(signal.state, tone, visual.motionLevel, Modifier.matchParentSize())
            }
            Row(Modifier.heightIn(min = 52.dp).padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Zone6IslandMark(signal.state, tone, !movement, Modifier.size(32.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    AnimatedContent(signal.label to (routeDetail ?: signal.detail),
                        transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                        label = "Dock state") { (title, detail) ->
                        Column {
                            Text(uiText(title), style = MaterialTheme.typography.titleSmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            detail?.let { Text(uiText(it), style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    val progress = signal.progress?.takeIf { it.isFinite() && it in 0f..1f }
                    if (progress != null) LinearProgressIndicator(progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp), color = tone)
                }
                when {
                    interaction.primary == HaloOrbPrimary.REVIEW ->
                        TextButton(onPrimary, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Review"), color = tone) }
                    interaction.canStop -> TextButton(onStop, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Stop")) }
                    more != null -> TextButton(more, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("More")) }
                }
            }
        }
    }
}

@Composable
internal fun Zone6IslandMark(state: HaloSignalState, tint: androidx.compose.ui.graphics.Color,
                             reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val raw = when (state) {
        HaloSignalState.LISTENING, HaloSignalState.CAPTURING ->
            if (dark) R.raw.halo_z6_listen_wave_dark else R.raw.halo_z6_listen_wave_light
        HaloSignalState.SUCCESS -> if (dark) R.raw.halo_z6_receipt_bloom_dark else R.raw.halo_z6_receipt_bloom_light
        else -> null
    }
    val foreground = com.halo.app.ui.fx.rememberHaloMotionForeground()
    val constrained = com.halo.app.ui.fx.rememberHaloMotionConstrained()
    if (raw == null || !foreground || constrained || reducedMotion) {
        HaloIcon(state.orbGlyph(), null, modifier, tint)
        return
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(raw))
    val dynamic = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.COLOR, tint.toArgb(), "**"),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, tint.toArgb(), "**"),
    )
    if (composition == null) HaloIcon(state.orbGlyph(), null, modifier, tint)
    else LottieAnimation(composition, modifier = modifier, dynamicProperties = dynamic,
        iterations = if (state == HaloSignalState.LISTENING || state == HaloSignalState.CAPTURING) LottieConstants.IterateForever else 1)
}

@Composable
private fun Zone6IslandEdge(state: HaloSignalState, tint: androidx.compose.ui.graphics.Color,
                            motion: HaloLookMotion, modifier: Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val raw = when {
        state == HaloSignalState.ERROR -> if (dark) R.raw.halo_z6_error_soften_dark else R.raw.halo_z6_error_soften_light
        state == HaloSignalState.WAITING && motion == HaloLookMotion.FULL ->
            if (dark) R.raw.halo_z6_need_glow_dark else R.raw.halo_z6_need_glow_light
        else -> return
    }
    key(raw) {
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(raw))
        val colors = rememberLottieDynamicProperties(
            rememberLottieDynamicProperty(LottieProperty.COLOR, tint.toArgb(), "**"),
            rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, tint.toArgb(), "**"),
        )
        LottieAnimation(composition, modifier = modifier, dynamicProperties = colors,
            iterations = if (state == HaloSignalState.WAITING) LottieConstants.IterateForever else 1,
            contentScale = ContentScale.FillBounds)
    }
}
