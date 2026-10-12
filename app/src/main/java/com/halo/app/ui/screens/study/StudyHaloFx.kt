package com.halo.app.ui.screens.study

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.theme.rememberReducedMotion

/**
 * Study's single-point-of-attention Halo FX: a quiet, breathing outline around the
 * action that is useful *now*, not permanent motion on every card.
 *
 * There is no click handler on the decoration: semantic focus remains on the real button.
 * Motion stops off-screen and respects Android's reduced-motion preference.
 */
@Composable
internal fun StudyFocusFrame(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reducedMotion by rememberReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val foreground = remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground.value = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val moving = active && !reducedMotion && foreground.value
    val alpha = if (moving) {
        val transition = rememberInfiniteTransition(label = "study_halo_attention")
        val opacity by transition.animateFloat(
            initialValue = 0.36f,
            targetValue = 0.82f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "study_focus_outline",
        )
        opacity
    } else if (active) 0.70f else 0.18f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (active) BorderStroke(
            width = 2.dp,
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = alpha * 0.60f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = alpha),
                ),
            ),
        ) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)),
    ) {
        Column(Modifier.padding(8.dp), content = content)
    }
}

@Composable
internal fun StudyFocusAction(
    text: String,
    glyph: HaloGlyph,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = enabled,
) {
    StudyFocusFrame(active = active && enabled, modifier = modifier) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            StudyActionLabel(glyph, text)
        }
    }
}
