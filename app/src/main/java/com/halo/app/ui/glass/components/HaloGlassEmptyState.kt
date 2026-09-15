package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HaloGlassEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = HaloGlassColors.TextMuted,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(HaloGlassTokens.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(56.dp)
                    .padding(bottom = 16.dp)
            )
        }

        Text(
            text = title,
            style = HaloTypography.titleMedium,
            color = HaloGlassColors.TextPrimary,
            modifier = Modifier.padding(bottom = if (description != null) 8.dp else 0.dp)
        )

        if (description != null) {
            Text(
                text = description,
                style = HaloTypography.bodyMedium,
                color = HaloGlassColors.TextSecondary,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        if (action != null) {
            action()
        }
    }
}
