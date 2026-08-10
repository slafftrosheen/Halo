package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.halo.app.ui.glass.material.HaloGlassColors

/**
 * Glass divider component that replaces legacy dividers.
 * Provides a subtle line separator with glass-appropriate styling.
 */
@Composable
fun HaloGlassDivider(
    modifier: Modifier = Modifier,
    thickness: androidx.compose.ui.unit.Dp = 1.dp,
    color: androidx.compose.ui.graphics.Color = HaloGlassColors.BorderSubtle,
    startIndent: androidx.compose.ui.unit.Dp = 0.dp,
    endIndent: androidx.compose.ui.unit.Dp = 0.dp
) {
    androidx.compose.foundation.layout.Divider(
        modifier = modifier
            .then(
                Modifier
                    .fillMaxWidth()
                    .height(thickness)
                    .padding(start = startIndent, end = endIndent)
            ),
        color = color
    )
}

/**
 * Horizontal glass divider that spans full width with standard padding.
 */
@Composable
fun HaloGlassHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: androidx.compose.ui.unit.Dp = 1.dp
) {
    HaloGlassDivider(
        modifier = modifier,
        thickness = thickness,
        startIndent = androidx.compose.ui.glass.material.HaloGlassTokens.md,
        endIndent = androidx.compose.ui.glass.material.HaloGlassTokens.md
    )
}

/**
 * Vertical glass divider for use in rows/columns.
 */
@Composable
fun HaloGlassVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: androidx.compose.ui.unit.Dp = 1.dp,
    height: androidx.compose.ui.unit.Dp = 24.dp
) {
    androidx.compose.foundation.layout.Divider(
        modifier = modifier
            .then(
                Modifier
                    .width(thickness)
                    .height(height)
            ),
        color = HaloGlassColors.BorderSubtle
    )
}