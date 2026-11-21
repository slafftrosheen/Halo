package com.halo.app.ui.zone6

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
import com.halo.app.ui.icons.HaloGlyph
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
    val tone = when (signal.state) {
        HaloSignalState.WAITING -> colors.tertiary
        HaloSignalState.ERROR -> colors.error
        HaloSignalState.OFFLINE -> if (colors.background.luminance() < .5f)
            androidx.compose.ui.graphics.Color(0xFFB6DFE2) else androidx.compose.ui.graphics.Color(0xFF205A78)
        else -> colors.primary
    }
    val duration = if (reducedMotion) 150 else if (rememberVisualPreferences().motionLevel == HaloLookMotion.FULL) 550 else 220
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(26.dp), color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, tone.copy(alpha = .5f)),
    ) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Zone6IslandMark(signal.state, tone, reducedMotion, Modifier.size(28.dp))
            AnimatedContent(signal.label to (routeDetail ?: signal.detail), modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) }, label = "Dock state") { (title, detail) ->
                Column {
                    Text(uiText(title), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    detail?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            val progress = signal.progress?.takeIf { it.isFinite() && it in 0f..1f }
            if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(44.dp))
            when {
                interaction.primary == HaloOrbPrimary.REVIEW ->
                    TextButton(onPrimary, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Review"), color = tone) }
                interaction.canStop -> TextButton(onStop, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Stop")) }
                more != null -> TextButton(more, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("More")) }
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
        HaloSignalState.ERROR -> if (dark) R.raw.halo_z6_error_soften_dark else R.raw.halo_z6_error_soften_light
        else -> null
    }
    val foreground = com.halo.app.ui.fx.rememberHaloMotionForeground()
    val constrained = com.halo.app.ui.fx.rememberHaloMotionConstrained()
    if (raw == null || !foreground || constrained || reducedMotion) {
        val glyph = when (state) {
            HaloSignalState.LISTENING, HaloSignalState.CAPTURING -> HaloGlyph.LISTEN
            HaloSignalState.WAITING -> HaloGlyph.APPROVAL
            HaloSignalState.SUCCESS -> HaloGlyph.SUCCESS
            HaloSignalState.ERROR -> HaloGlyph.ERROR
            HaloSignalState.OFFLINE -> HaloGlyph.OFFLINE
            else -> HaloGlyph.HALO
        }
        HaloIcon(glyph, null, modifier, tint)
        return
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(raw))
    val dynamic = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.COLOR, tint.toArgb(), "**"),
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, tint.toArgb(), "**"),
    )
    if (composition == null) HaloIcon(HaloGlyph.HALO, null, modifier, tint)
    else LottieAnimation(composition, modifier = modifier, dynamicProperties = dynamic,
        iterations = if (state == HaloSignalState.LISTENING || state == HaloSignalState.CAPTURING) LottieConstants.IterateForever else 1)
}
