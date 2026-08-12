package com.halo.app.ui.glass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors

/**
 * A glass-styled divider using the subtle border color.
 */
@Composable
fun HaloGlassDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = HaloGlassColors.BorderSubtle,
    startIndent: Dp = 0.dp,
    endIndent: Dp = 0.dp
) {
    HorizontalDivider(
        modifier = modifier
            .padding(start = startIndent, end = endIndent),
        thickness = thickness,
        color = color
    )
}

/**
 * Specialized horizontal divider with pre-defined glass styling.
 */
@Composable
fun HaloGlassHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp
) {
    HaloGlassDivider(
        modifier = modifier
            .fillMaxWidth()
            .background(HaloGlassColors.BorderSubtle),
        thickness = thickness
    )
}

/**
 * Specialized vertical divider with pre-defined glass styling.
 */
@Composable
fun HaloGlassVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    height: Dp = 24.dp
) {
    Box(
        modifier = modifier
            .width(thickness)
            .height(height)
            .background(HaloGlassColors.BorderSubtle)
    )
}
