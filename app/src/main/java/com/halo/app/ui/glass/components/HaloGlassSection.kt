package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens

/**
 * Primary grouping primitive for Halo's glass UI.
 * Keeps section chrome deliberately quiet so content, controls and state carry
 * the visual hierarchy instead of a collection of heavy cards.
 */
@Composable
fun HaloGlassSection(
    modifier: Modifier = Modifier,
    title: String,
    surfaceModifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    material: HaloGlassMaterial = HaloGlassMaterials.Standard,
    titleStyle: TextStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = HaloGlassColors.TextSecondary,
        letterSpacing = 1.2.sp
    ),
    spacing: Dp = HaloGlassTokens.sm,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HaloGlassTokens.xs)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HaloGlassTokens.sm, vertical = HaloGlassTokens.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title.uppercase(), style = titleStyle)
            trailing()
        }

        HaloGlassSurface(
            modifier = surfaceModifier.fillMaxWidth(),
            material = material,
            shape = HaloGlassTokens.largeShape
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content
            )
        }
    }
}

object HaloGlassSection {
    @Composable
    fun Standard(
        modifier: Modifier = Modifier,
        title: String,
        content: @Composable ColumnScope.() -> Unit
    ) = HaloGlassSection(
        modifier = modifier,
        title = title,
        material = HaloGlassMaterials.Standard,
        content = content
    )

    @Composable
    fun Background(
        modifier: Modifier = Modifier,
        title: String,
        content: @Composable ColumnScope.() -> Unit
    ) = HaloGlassSection(
        modifier = modifier,
        title = title,
        material = HaloGlassMaterials.Background,
        content = content
    )

    @Composable
    fun Elevated(
        modifier: Modifier = Modifier,
        title: String,
        content: @Composable ColumnScope.() -> Unit
    ) = HaloGlassSection(
        modifier = modifier,
        title = title,
        material = HaloGlassMaterials.Elevated,
        content = content
    )

    @Composable
    fun Strong(
        modifier: Modifier = Modifier,
        title: String,
        content: @Composable ColumnScope.() -> Unit
    ) = HaloGlassSection(
        modifier = modifier,
        title = title,
        material = HaloGlassMaterials.Strong,
        content = content
    )
}
