package com.halo.app.ui.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.theme.rememberReducedMotion

internal fun haloAttentionMotionAllowed(active: Boolean, animate: Boolean, reducedMotion: Boolean, foreground: Boolean) =
    active && animate && !reducedMotion && foreground

/** Decoration has no click/gesture handler. Lists use static emphasis by default. */
@Composable
fun HaloFocusFrame(active: Boolean, modifier: Modifier = Modifier, animate: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val reducedMotion by rememberReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState == Lifecycle.State.RESUMED) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState == Lifecycle.State.RESUMED }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val alpha = if (haloAttentionMotionAllowed(active, animate, reducedMotion, foreground)) {
        val transition = rememberInfiniteTransition(label = "halo_attention")
        val opacity by transition.animateFloat(0.36f, 0.82f,
            infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "halo_focus_outline")
        opacity
    } else if (active) 0.70f else 0.18f
    Surface(modifier = modifier, shape = HaloShapes.card,
        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (active) BorderStroke(2.dp, Brush.linearGradient(listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.28f),
            MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        ))) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)),
    ) { Column(Modifier.padding(8.dp), content = content) }
}

/** Caller chooses one useful next action; disabled actions never receive emphasis. */
@Composable
fun HaloFocusAction(text: String, glyph: HaloGlyph, enabled: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, active: Boolean = enabled, animate: Boolean = false,
    style: HaloActionStyle = HaloActionStyle.PRIMARY) {
    HaloFocusFrame(active && enabled, modifier, animate) {
        HaloActionButton(onClick, Modifier.fillMaxWidth(), enabled, style) {
            HaloIcon(glyph, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, modifier = Modifier.weight(1f))
        }
    }
}
