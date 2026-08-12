package com.halo.app.ui.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloRuntimeState

/**
 * A clickable glass-enhanced version of the Halo State Orb.
 * This is the primary interaction point for manual runtime control.
 */
@Composable
fun HaloFloatingMic(
    state: HaloRuntimeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    HaloGlassSurface(
        modifier = modifier
            .size(size)
            .clip(HaloGlassTokens.circleShape)
            .clickable { onClick() },
        material = HaloGlassMaterials.Strong,
        shape = HaloGlassTokens.circleShape
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            HaloStateOrb(
                state = state,
                size = size * 0.8f
            )
        }
    }
}
