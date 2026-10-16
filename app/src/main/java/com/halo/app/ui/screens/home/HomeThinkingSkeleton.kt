package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.fx.haloShimmer
import com.halo.sdk.provider.CognitiveSessionState

/** Placeholder is a rendering affordance only — never a fabricated response.
 * Suppressed during approval/review, when text is available or on failure.
 */
internal fun homeShouldShowThinkingSkeleton(
    session: CognitiveSessionState,
    visibleResponse: String,
    needsReview: Boolean,
): Boolean = !needsReview && visibleResponse.isBlank() &&
    (session is CognitiveSessionState.PreparingContext || session is CognitiveSessionState.Reasoning)

@Composable
internal fun HomeThinkingSkeleton() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = scheme.surfaceContainerLow,
        shape = HaloShapes.hero,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (fraction in listOf(.42f, 1f, .86f, .62f)) {
                Spacer(Modifier.fillMaxWidth(fraction).height(if (fraction == .42f) 14.dp else 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .haloShimmer(scheme.surfaceContainerHigh, scheme.primaryContainer.copy(alpha = .54f)))
            }
        }
    }
}
