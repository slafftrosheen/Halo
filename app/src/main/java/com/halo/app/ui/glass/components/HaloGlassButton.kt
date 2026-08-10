package com.halo.app.ui.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressIndication
import androidx.compose.foundation.interaction.Indication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.HaloGlassSurface
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloGlassShapes

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
    contentColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    material: HaloGlassMaterial = HaloGlassMaterials.Interactive
) {
    val isPressed by remember { mutableStateOf(false) }
    
    // Determine colors
    val buttonContentColor = if (contentColor != androidx.compose.ui.graphics.Color.Unspecified) {
        contentColor
    } else {
        HaloGlassColors.TextPrimary
    }
    
    val buttonContainerColor = if (!enabled) {
        material.containerColor.copy(alpha = 0.3f)
    } else if (isPressed) {
        material.containerColor
    } else {
        material.containerColor
    }
    
    HaloGlassSurface(
        modifier = modifier
            .then(
                Modifier
                    .clickable(
                        onClick = { if (enabled) onClick() },
                        enabled = enabled
                    )
                    .then(
                        Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = { isPressed = true },
                                    onRelease = { isPressed = false }
                                )
                            }
                    )
            ),
        material = material.copy(
            containerColor = buttonContainerColor
        ),
        shape = HaloGlassShapes.pill
    ) {
        Box(
            modifier = Modifier
                .padding(
                    horizontal = HaloGlassTokens.md,
                    vertical = HaloGlassTokens.xs
                )
                .align(androidx.compose.ui.Alignment.Center)
        ) {
            Text(
                text = text,
                color = buttonContentColor,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
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