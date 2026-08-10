package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import com.halo.app.ui.glass.components.HaloGlassSurface
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassShapes
import com.halo.app.ui.glass.material.HaloGlassTokens
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Glass dialog component that replaces legacy dialog implementations.
 * Provides a glass-material surface for modal dialogs with appropriate elevation.
 */
@Composable
fun HaloGlassDialog(
    modifier: Modifier = Modifier,
    title: String? = null,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
    material: HaloGlassMaterial = HaloGlassMaterials.Elevated,
    shape: androidx.compose.foundation.shape.Shape = HaloGlassShapes.large,
    properties: DialogProperties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = true
    )
) {
    // Calculate dialog width based on screen size
    val dialogWidth = LocalConfiguration.current.screenWidthDp.dp * 0.88
    val maxWidth = 600.dp // Maximum width for large screens
    
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        HaloGlassSurface(
            modifier = modifier
                .then(
                    Modifier
                        .width(dialogWidth.coerceAtMost(maxWidth))
                        .padding(16.dp) // Standard dialog padding
                        .glassElevation(HaloGlassTokens.level4) // 20.dp elevation for dialogs
                ),
            material = material,
            shape = shape
        ) {
            // Optional title
            title?.let {
                Text(
                    text = it,
                    modifier = Modifier
                        .padding(bottom = HaloGlassTokens.md),
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = HaloGlassColors.TextPrimary
                )
            }
            
            // Dialog content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = HaloGlassTokens.lg,
                        end = HaloGlassTokens.lg,
                        bottom = HaloGlassTokens.lg
                    )
            ) {
                content()
            }
        }
    }
}

/**
 * Extended glass dialog with different variants.
 */
object HaloGlassDialog {
    /**
     * Creates a standard glass dialog (elevated material)
     */
    @Composable
    fun Standard(
        modifier: Modifier = Modifier,
        title: String? = null,
        onDismissRequest: () -> Unit,
        content: @Composable () -> Unit
    ) {
        HaloGlassDialog(
            modifier = modifier,
            title = title,
            onDismissRequest = onDismissRequest,
            content = content,
            material = HaloGlassMaterials.Elevated
        )
    }

    /**
     * Creates a background glass dialog (for full-screen dialogs)
     */
    @Composable
    fun Background(
        modifier: Modifier = Modifier,
        title: String? = null,
        onDismissRequest: () -> Unit,
        content: @Composable () -> Unit
    ) {
        HaloGlassDialog(
            modifier = modifier,
            title = title,
            onDismissRequest = onDismissRequest,
            content = content,
            material = HaloGlassMaterials.Background,
            shape = HaloGlassShapes.large
        )
    }

    /**
     * Creates a strong glass dialog (for critical alerts/confirmations)
     */
    @Composable
    fun Strong(
        modifier: Modifier = Modifier,
        title: String? = null,
        onDismissRequest: () -> Unit,
        content: @Composable () -> Unit
    ) {
        HaloGlassDialog(
            modifier = modifier,
            title = title,
            onDismissRequest = onDismissRequest,
            content = content,
            material = HaloGlassMaterials.Strong
        )
    }

    /**
     * Creates an informative glass dialog (uses accent coloring)
     */
    @Composable
    fun Accent(
        modifier: Modifier = Modifier,
        title: String? = null,
        onDismissRequest: () -> Unit,
        content: @Composable () -> Unit
    ) {
        HaloGlassDialog(
            modifier = modifier,
            title = title,
            onDismissRequest = onDismissRequest,
            content = content,
            material = HaloGlassMaterials.Standard.copy(
                containerColor = HaloGlassColors.AccentContainer
            )
        )
    }
}