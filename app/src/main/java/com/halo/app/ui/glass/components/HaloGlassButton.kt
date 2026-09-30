package com.halo.app.ui.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens

/**
 * Glass button component that replaces the legacy HaloButton.
 * Uses the Halo Glass material system with a visible, state-aware touch response.
 */
@Composable
fun HaloGlassButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isPrimary: Boolean = true,
    contentColor: Color = Color.Unspecified,
    material: HaloGlassMaterial = HaloGlassMaterials.Interactive
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val resolvedMaterial = if (isPrimary && material == HaloGlassMaterials.Interactive) {
        material.copy(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary)
    } else material

    val baseContentColor = if (contentColor != Color.Unspecified) {
        contentColor
    } else {
        resolvedMaterial.contentColor
    }
    val buttonContentColor = if (enabled) {
        baseContentColor
    } else {
        baseContentColor.copy(alpha = 0.45f)
    }

    val buttonContainerColor = when {
        !enabled -> resolvedMaterial.containerColor.copy(alpha = 0.22f)
        isPressed -> resolvedMaterial.containerColor.copy(alpha = 0.78f)
        else -> resolvedMaterial.containerColor
    }

    HaloGlassSurface(
        modifier = modifier
            .defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            ),
        material = resolvedMaterial.copy(
            containerColor = buttonContainerColor,
            elevation = if (isPressed) 2.dp else resolvedMaterial.elevation
        ),
        contentPadding = 0.dp,
        shape = HaloGlassTokens.pillShape
    ) {
        Box(
            modifier = Modifier
                .padding(
                    horizontal = HaloGlassTokens.md,
                    vertical = 8.dp
                )
                .align(Alignment.Center)
        ) {
            Text(
                text = text,
                color = buttonContentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Extended glass button with different variants.
 */
object HaloGlassButton {
    @Composable
    fun Primary(
        modifier: Modifier = Modifier,
        text: String,
        onClick: () -> Unit,
        enabled: Boolean = true
    ) {
        HaloGlassButton(
            modifier = modifier,
            text = text,
            onClick = onClick,
            enabled = enabled,
            isPrimary = true,
            material = HaloGlassMaterials.Interactive.copy(
                containerColor = HaloGlassColors.Accent,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
            )
        )
    }

    @Composable
    fun Secondary(
        modifier: Modifier = Modifier,
        text: String,
        onClick: () -> Unit,
        enabled: Boolean = true
    ) {
        HaloGlassButton(
            modifier = modifier,
            text = text,
            onClick = onClick,
            enabled = enabled,
            isPrimary = false,
            material = HaloGlassMaterials.Interactive.copy(
                containerColor = HaloGlassColors.Glass
            )
        )
    }

    @Composable
    fun Ghost(
        modifier: Modifier = Modifier,
        text: String,
        onClick: () -> Unit,
        enabled: Boolean = true
    ) {
        HaloGlassButton(
            modifier = modifier,
            text = text,
            onClick = onClick,
            enabled = enabled,
            isPrimary = false,
            material = HaloGlassMaterials.Interactive.copy(
                containerColor = HaloGlassColors.BackgroundSecondary
            )
        )
    }

    @Composable
    fun Danger(
        modifier: Modifier = Modifier,
        text: String,
        onClick: () -> Unit,
        enabled: Boolean = true
    ) {
        HaloGlassButton(
            modifier = modifier,
            text = text,
            onClick = onClick,
            enabled = enabled,
            isPrimary = true,
            material = HaloGlassMaterials.Interactive.copy(
                containerColor = HaloGlassColors.Danger,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onError
            )
        )
    }
}
