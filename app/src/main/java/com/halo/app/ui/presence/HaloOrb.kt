package com.halo.app.ui.presence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.state.toSignalPresentation

@Deprecated("Replaced by HaloSignalPresentation")
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

/** Compatibility wrapper delegating HaloOrb to HaloSignalFace. */
@Composable
fun HaloOrb(
    presentation: HaloOrbPresentation,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp
) {
    val basePresentation = presentation.phase.toSignalPresentation(
        detail = presentation.source,
        activityLevel = presentation.inputLevel,
        reducedMotion = presentation.reducedMotion
    )
    HaloSignalFace(
        presentation = if (presentation.failed) basePresentation.copy(state = HaloSignalState.ERROR, label = "Needs attention") else basePresentation,
        modifier = modifier,
        size = size
    )
}
