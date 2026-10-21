package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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
    material: HaloGlassMaterial = HaloGlassMaterials.Elevated,
    shape: Shape = HaloGlassTokens.xlShape,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable BoxScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        HaloGlassSurface(
            modifier = modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .wrapContentHeight(),
            material = material,
            shape = shape,
            contentPadding = 20.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        color = HaloGlassColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(bottom = 6.dp).semantics { heading() }
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
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            content = content
        )
    }
}
