package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens

/**
 * A glass section that groups content under a title with vertical layout.
 */
@Composable
fun HaloGlassSection(
    modifier: Modifier = Modifier,
    title: String,
    surfaceModifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    material: HaloGlassMaterial = HaloGlassMaterials.Standard,
    titleStyle: TextStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = HaloGlassColors.TextSecondary
    ),
    spacing: Dp = HaloGlassTokens.md,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = HaloGlassTokens.md,
                    vertical = HaloGlassTokens.sm
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = titleStyle
            )

            trailing()
        }

        HaloGlassSurface(
            modifier = surfaceModifier.fillMaxWidth(),
            material = material,
            shape = HaloGlassTokens.mediumShape
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content
            )
        }
    }
}

/**
 * Predefined glass section variants.
 */
object HaloGlassSection {
    @Composable
    fun Standard(
        modifier: Modifier = Modifier,
        title: String,
        content: @Composable ColumnScope.() -> Unit
    ) {
        HaloGlassSection(
            modifier = modifier,
            title = title,
            material = HaloGlassMaterials.Standard,
            content = content
        )
    }

    @Composable
    fun Background(
        modifier: Modifier = Modifier,
        title: String,
        content: @Composable ColumnScope.() -> Unit
    ) {
        HaloGlassSection(
            modifier = modifier,
            title = title,
            material = HaloGlassMaterials.Background,
            content = content
        )
    }

    @Composable
    fun Elevated(
        modifier: Modifier = Modifier,
        title: String,
        content: @Composable ColumnScope.() -> Unit
    ) {
        HaloGlassSection(
            modifier = modifier,
            title = title,
            material = HaloGlassMaterials.Elevated,
            content = content
        )
    }

    @Composable
    fun Strong(
        modifier: Modifier = Modifier,
        title: String,
        content: @Composable ColumnScope.() -> Unit
    ) {
        HaloGlassSection(
            modifier = modifier,
            title = title,
            material = HaloGlassMaterials.Strong,
            content = content
        )
    }
}
