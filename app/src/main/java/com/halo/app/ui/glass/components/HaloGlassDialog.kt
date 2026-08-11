package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloGlassColors

/**
 * Glass dialog for modal interactions.
 * Uses elevated glass material with higher blur and shadows.
 */
@Composable
fun HaloGlassDialog(
    modifier: Modifier = Modifier,
    title: String? = null,
    onDismissRequest: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
    material: HaloGlassMaterial = HaloGlassMaterials.Elevated,
    shape: Shape = HaloGlassTokens.xlShape,
    properties: DialogProperties = DialogProperties()
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        HaloGlassSurface(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            material = material,
            shape = shape
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        color = HaloGlassColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    content = content
                )
            }
        }
    }
}

/**
 * Semantic variants for glass dialogs.
 */
object HaloGlassDialog {
    @Composable
    fun Standard(
        modifier: Modifier = Modifier,
        title: String? = null,
        onDismissRequest: () -> Unit,
        content: @Composable BoxScope.() -> Unit
    ) {
        HaloGlassDialog(
            modifier = modifier,
            title = title,
            onDismissRequest = onDismissRequest,
            material = HaloGlassMaterials.Standard,
            content = content
        )
    }

    @Composable
    fun Background(
        modifier: Modifier = Modifier,
        title: String? = null,
        onDismissRequest: () -> Unit,
        content: @Composable BoxScope.() -> Unit
    ) {
        HaloGlassDialog(
            modifier = modifier,
            title = title,
            onDismissRequest = onDismissRequest,
            material = HaloGlassMaterials.Background,
            content = content
        )
    }

    @Composable
    fun Strong(
        modifier: Modifier = Modifier,
        title: String? = null,
        onDismissRequest: () -> Unit,
        content: @Composable BoxScope.() -> Unit
    ) {
        HaloGlassDialog(
            modifier = modifier,
            title = title,
            onDismissRequest = onDismissRequest,
            material = HaloGlassMaterials.Strong,
            content = content
        )
    }

    @Composable
    fun Accent(
        modifier: Modifier = Modifier,
        title: String? = null,
        onDismissRequest: () -> Unit,
        content: @Composable BoxScope.() -> Unit
    ) {
        HaloGlassDialog(
            modifier = modifier,
            title = title,
            onDismissRequest = onDismissRequest,
            material = HaloGlassMaterials.Elevated.copy(
                containerColor = HaloGlassColors.Accent.copy(alpha = 0.1f)
            ),
            content = content
        )
    }
}
