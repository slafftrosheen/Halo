package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassTokens

/**
 * Glass section component that replaces the legacy HaloSection.
 * Provides a titled section with glass styling.
 *
 * @param modifier The modifier to apply to the section
 * @param title The section title text
 * @param modifier Modifier for the section container
 * @param action Optional action to display at the end of the title bar
 * @param content The content to display within the section
 * @param material The glass material for the section container
 * @param titleStyle Style for the section title
 * @param dividerHeight Height of the divider under the title
 * @param dividerColor Color of the divider under the title
 */
@Composable
fun HaloGlassSection(
    modifier: Modifier = Modifier,
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
    material: HaloGlassMaterial = HaloGlassMaterials.Standard,
    titleStyle: androidx.compose.ui.text.style.TextStyle = androidx.compose.ui.text.style.TextStyle(
        fontSize = 16.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = androidx.compose.ui.graphics.Color.Unspecified
    ),
    dividerHeight: Dp = 1.dp,
    dividerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    HaloGlassCard(
        modifier = modifier
            .then(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = dividerHeight)
            ),
        material = material
    ) {
        // Header with title and optional action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = HaloGlassTokens.md,
                    bottom = HaloGlassTokens.xs,
                    start = HaloGlassTokens.md,
                    end = HaloGlassTokens.md
                )
        ) {
            Text(
                text = title,
                style = titleStyle
                    .copy(
                        color = if (titleStyle.color == androidx.compose.ui.graphics.Color.Unspecified)
                            HaloGlassColors.TextPrimary
                        else titleStyle.color
                    )
            )
            Spacer(modifier = Modifier.weight(1f))
            action()
        }

        // Divider line
        androidx.compose.foundation.layout.Divider(
            modifier = Modifier
                .fillMaxWidth()
                .height(dividerHeight)
                .padding(start = HaloGlassTokens.md, end = HaloGlassTokens.md),
            color = if (dividerColor == androidx.compose.ui.graphics.Color.Unspecified)
                HaloGlassColors.BorderSubtle
            else dividerColor
        )

        // Content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = HaloGlassTokens.xs,
                    bottom = HaloGlassTokens.md,
                    start = HaloGlassTokens.md,
                    end = HaloGlassTokens.md
                )
        ) {
            content()
        }
    }
}

/**
 * Extended glass section with different material levels.
 */
object HaloGlassSection {
    /**
     * Creates a section with background material level
     */
    @Composable
    fun Background(
        modifier: Modifier = Modifier,
        title: String,
        content: @Composable () -> Unit,
        action: @Composable () -> Unit = {}
    ) {
        HaloGlassSection(
            modifier = modifier,
            title = title,
            content = content,
            action = action,
            material = HaloGlassMaterials.Background
        )
    }

    /**
     * Creates a section with standard material level (most common use case)
     */
    @Composable
    fun Standard(
        modifier: Modifier = Modifier,
        title: String,
        content: @Composable () -> Unit,
        action: @Composable () -> Unit = {}
    ) {
        HaloGlassSection(
            modifier = modifier,
            title = title,
            content = content,
            action = action,
            material = HaloGlassMaterials.Standard
        )
    }

    /**
     * Creates a section with elevated material level
     */
    @Composable
    fun Elevated(
        modifier: Modifier = Modifier,
        title: String,
        content: @Composable () -> Unit,
        action: @Composable () -> Unit = {}
    ) {
        HaloGlassSection(
            modifier = modifier,
            title = title,
            content = content,
            action = action,
            material = HaloGlassMaterials.Elevated
        )
    }

    /**
     * Creates a section with strong material level
     */
    @Composable
    fun Strong(
        modifier: Modifier = Modifier,
        title: String,
        content: @Composable () -> Unit,
        action: @Composable () -> Unit = {}
    ) {
        HaloGlassSection(
            modifier = modifier,
            title = title,
            content = content,
            action = action,
            material = HaloGlassMaterials.Strong
        )
    }
}