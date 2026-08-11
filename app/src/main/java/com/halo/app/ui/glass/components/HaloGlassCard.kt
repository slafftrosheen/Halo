package com.halo.app.ui.glass.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens

/**
 * Glass card component for grouping related content.
 * Provides consistent styling based on Halo Glass materials.
 */
@Composable
fun HaloGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication? = null,
    enabled: Boolean = true,
    material: HaloGlassMaterial = HaloGlassMaterials.Standard,
    shape: Shape = HaloGlassTokens.largeShape,
    content: @Composable BoxScope.() -> Unit
) {
    HaloGlassSurface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = indication,
                        enabled = enabled,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        material = material,
        shape = shape,
        content = content
    )
}

/**
 * Static accessors for common glass card variants.
 */
object HaloGlassCard {
    @Composable
    fun Background(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable BoxScope.() -> Unit
    ) {
        HaloGlassCard(
            modifier = modifier,
            onClick = onClick,
            material = HaloGlassMaterials.Background,
            content = content
        )
    }

    @Composable
    fun Standard(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable BoxScope.() -> Unit
    ) {
        HaloGlassCard(
            modifier = modifier,
            onClick = onClick,
            material = HaloGlassMaterials.Standard,
            content = content
        )
    }

    @Composable
    fun Elevated(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable BoxScope.() -> Unit
    ) {
        HaloGlassCard(
            modifier = modifier,
            onClick = onClick,
            material = HaloGlassMaterials.Elevated,
            content = content
        )
    }

    @Composable
    fun Strong(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable BoxScope.() -> Unit
    ) {
        HaloGlassCard(
            modifier = modifier,
            onClick = onClick,
            material = HaloGlassMaterials.Strong,
            content = content
        )
    }

    @Composable
    fun Interactive(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable BoxScope.() -> Unit
    ) {
        HaloGlassCard(
            modifier = modifier,
            onClick = onClick,
            material = HaloGlassMaterials.Interactive,
            content = content
        )
    }
}
