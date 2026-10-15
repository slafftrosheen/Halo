@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.halo.app.ui.presence

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android MiniHaloActivity is a separate window/composition, so no actual
 * shared element can cross its Activity boundary. Only Navigation Compose
 * transitions within one Activity share the canonical "halo-orb" element.
 */
internal fun canShareHaloOrb(sameActivity: Boolean, reducedMotion: Boolean): Boolean =
    sameActivity && !reducedMotion

@Composable
fun haloSharedOrbModifier(
    shared: SharedTransitionScope?,
    visibility: AnimatedVisibilityScope?,
    enabled: Boolean,
): Modifier {
    if (shared == null || visibility == null || !enabled) return Modifier
    return with(shared) {
        Modifier.sharedElement(
            rememberSharedContentState(key = "halo-orb"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ ->
                tween(650, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
            },
        )
    }
}
