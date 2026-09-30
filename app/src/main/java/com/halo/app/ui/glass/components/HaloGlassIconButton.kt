package com.halo.app.ui.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens

@Composable
fun HaloGlassIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = HaloGlassColors.TextPrimary,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    material: HaloGlassMaterial = HaloGlassMaterials.Interactive
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val contentAlpha = when {
        !enabled -> 0.38f
        isPressed -> 0.7f
        else -> 1f
    }

    val containerColor = when {
        !enabled -> material.containerColor.copy(alpha = 0.22f)
        isPressed -> material.containerColor.copy(alpha = 0.78f)
        else -> material.containerColor
    }

    HaloGlassSurface(
        modifier = modifier
            .size(size.coerceAtLeast(48.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            ),
        material = material.copy(
            containerColor = containerColor,
            elevation = if (isPressed) 2.dp else material.elevation
        ),
        contentPadding = 0.dp,
        shape = HaloGlassTokens.circleShape
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size.coerceAtLeast(48.dp))
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = tint.copy(alpha = contentAlpha),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

object HaloGlassIconButton {
    @Composable
    fun Primary(
        imageVector: ImageVector,
        contentDescription: String?,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        size: Dp = 48.dp
    ) {
        HaloGlassIconButton(
            imageVector = imageVector,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
            size = size,
            material = HaloGlassMaterials.Interactive.copy(
                containerColor = HaloGlassColors.Accent
            )
        )
    }

    @Composable
    fun Ghost(
        imageVector: ImageVector,
        contentDescription: String?,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        size: Dp = 48.dp
    ) {
        HaloGlassIconButton(
            imageVector = imageVector,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            size = size,
            material = HaloGlassMaterials.Interactive.copy(
                containerColor = HaloGlassColors.BackgroundSecondary
            )
        )
    }

    @Composable
    fun Danger(
        imageVector: ImageVector,
        contentDescription: String?,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        size: Dp = 48.dp
    ) {
        HaloGlassIconButton(
            imageVector = imageVector,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onError,
            size = size,
            material = HaloGlassMaterials.Interactive.copy(
                containerColor = HaloGlassColors.Danger
            )
        )
    }
}
