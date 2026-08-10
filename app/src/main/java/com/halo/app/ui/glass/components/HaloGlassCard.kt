package com.halo.app.ui.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressIndication
import androidx.compose.foundation.interaction.Indication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.ContentDrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInput
import androidx.compose.ui.unit.Dp
import com.halo.app.ui.glass.components.HaloGlassSurface
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloGlassShapes

/**
 * Glass card component that replaces the legacy HaloCard.
 * Uses the Halo Glass material system with 5 elevation levels.
 *
 * @param modifier The modifier to apply to the card
 * @param onClick Click listener (makes card clickable)
 * @param interactionSource Optional interaction source for custom interactions
 * @param indication Optional indication for press visual feedback
 * @param enabled Whether the card is enabled
 * @param material The glass material defining the card's appearance
 * @param shape The shape of the card (defaults to large rounded corners)
 * @param content The content to display within the card
 */
@Composable
fun HaloGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication = PressIndication(),
    enabled: Boolean = true,
    material: HaloGlassMaterial = HaloGlassMaterials.Standard,
    shape: androidx.compose.foundation.shape.Shape = HaloGlassShapes.large,
    content: @Composable () -> Unit
) {
    val clickableModifier = modifier
        .then(
            if (onClick != null && enabled) {
                modifier.clickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    onClick = onClick,
                    enabled = enabled
                )
            } else {
                modifier
            }
        )
        .then(
            Modifier
                .pointerInput(Unit) {
                    // Add ripple effect simulation for touch feedback
                    detectTapGestures(
                        onTap = { /* Handle tap for visual feedback */ }
                    )
                }
        )

    HaloGlassSurface(
        modifier = clickableModifier
            .then(
                // Add hover/focus indication
                Modifier
                    .pointerInput(Unit) {
                        // Hover/focus detection would go here
                    }
            ),
        material = material,
        shape = shape
    ) {
        content()
    }
}

/**
 * Extended glass card with elevation levels for different use cases.
 */
object HaloGlassCard {
    /**
     * Creates a card with background material level (full-screen backdrop)
     */
    @Composable
    fun Background(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable () -> Unit
    ) {
        HaloGlassCard(
            modifier = modifier,
            onClick = onClick,
            material = HaloGlassMaterials.Background,
            content = content
        )
    }

    /**
     * Creates a card with standard material level (most common use case)
     */
    @Composable
    fun Standard(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable () -> Unit
    ) {
        HaloGlassCard(
            modifier = modifier,
            onClick = onClick,
            material = HaloGlassMaterials.Standard,
            content = content
        )
    }

    /**
     * Creates a card with elevated material level (for raised surfaces)
     */
    @Composable
    fun Elevated(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable () -> Unit
    ) {
        HaloGlassCard(
            modifier = modifier,
            onClick = onClick,
            material = HaloGlassMaterials.Elevated,
            content = content
        )
    }

    /**
     * Creates a card with strong material level (for critical/focused elements)
     */
    @Composable
    fun Strong(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable () -> Unit
    ) {
        HaloGlassCard(
            modifier = modifier,
            onClick = onClick,
            material = HaloGlassMaterials.Strong,
            content = content
        )
    }

    /**
     * Creates a card with interactive material level (for buttons/interactives)
     */
    @Composable
    fun Interactive(
        modifier: Modifier = Modifier,
        onClick: (() -> Unit)? = null,
        content: @Composable () -> Unit
    ) {
        HaloGlassCard(
            modifier = modifier,
            onClick = onClick,
            material = HaloGlassMaterials.Interactive,
            content = content
        )
    }
}