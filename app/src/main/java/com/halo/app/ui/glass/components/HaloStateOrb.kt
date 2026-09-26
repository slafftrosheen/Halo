package com.halo.app.ui.glass.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.state.toSignalPresentation

/**
 * Halo's primary presence surface (delegated to canonical HaloSignalFace).
 */
@Composable
fun HaloStateOrb(
    state: HaloRuntimeState,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String = "Halo"
) {
    val presentation = state.toSignalPresentation()
    HaloSignalFace(
        presentation = presentation,
        modifier = modifier,
        size = size,
        onClick = onClick,
        onLongClick = onLongClick
    )
}
