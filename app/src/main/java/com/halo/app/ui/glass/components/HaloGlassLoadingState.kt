package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HaloGlassLoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
    progressColor: Color = HaloGlassColors.Accent,
    trackColor: Color = HaloGlassColors.GlassElevated,
    orbSize: Dp = 56.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(HaloGlassTokens.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HaloGlassIndeterminateCircularProgress(
            size = orbSize,
            strokeWidth = 4.dp,
            trackColor = trackColor,
            progressColor = progressColor,
            modifier = Modifier.padding(bottom = if (message != null) 16.dp else 0.dp)
        )

        if (message != null) {
            Text(
                text = message,
                style = HaloTypography.bodyMedium,
                color = HaloGlassColors.TextSecondary
            )
        }
    }
}
