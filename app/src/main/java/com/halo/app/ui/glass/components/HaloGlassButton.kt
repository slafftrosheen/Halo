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
 * Uses the Halo Glass material system with interactive level for touch feedback.
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
    
    // Determine colors
    val buttonContentColor = if (contentColor != Color.Unspecified) {
        contentColor
    } else {
        HaloGlassColors.TextPrimary
    }
    
    val buttonContainerColor = if (!enabled) {
        material.containerColor.copy(alpha = 0.3f)
    } else if (isPressed) {
        material.containerColor.copy(alpha = 0.9f)
    } else {
        material.containerColor
    }
    
    HaloGlassSurface(
        modifier = modifier
            .defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        material = material.copy(
            containerColor = buttonContainerColor,
            elevation = if (isPressed) 2.dp else material.elevation
        ),
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
    /**
     * Creates a primary glass button (uses accent background)
     */
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
                containerColor = HaloGlassColors.Accent
            )
        )
    }

    /**
     * Creates a secondary glass button (uses surface background)
     */
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

    /**
     * Creates a ghost/transparent glass button (minimal styling)
     */
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

    /**
     * Creates a danger glass button (uses danger color for destructive actions)
     */
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
                containerColor = HaloGlassColors.Danger
            )
        )
    }
}
