package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
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
fun HaloGlassErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector = Icons.Default.Warning,
    iconTint: Color = HaloGlassColors.Danger,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(HaloGlassTokens.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(48.dp)
                .padding(bottom = 16.dp)
        )

        if (title != null) {
            Text(
                text = title,
                style = HaloTypography.titleMedium,
                color = HaloGlassColors.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Text(
            text = message,
            style = HaloTypography.bodyMedium,
            color = HaloGlassColors.TextSecondary,
            modifier = Modifier.padding(bottom = if (onRetry != null) 24.dp else 0.dp)
        )

        if (onRetry != null && retryLabel != null) {
            HaloGlassButton.Secondary(
                text = retryLabel,
                onClick = onRetry
            )
        }
    }
}
