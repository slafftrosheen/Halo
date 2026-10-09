package com.halo.app.ui.glass.components

import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
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
    val style = when {
        material.containerColor == HaloGlassColors.Danger -> HaloActionStyle.STOP
        isPrimary -> HaloActionStyle.PRIMARY
        else -> HaloActionStyle.SECONDARY
    }
    HaloActionButton(onClick, modifier, enabled, style) {
        Text(text, color = contentColor)
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
