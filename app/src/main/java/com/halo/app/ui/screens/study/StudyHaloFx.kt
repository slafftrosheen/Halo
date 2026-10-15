package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.design.HaloFocusFrame
import com.halo.app.ui.icons.HaloGlyph

/** Study selects the attention target; rendering and lifecycle policy are shared. */
@Composable
internal fun StudyFocusFrame(active: Boolean, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) =
    HaloFocusFrame(active, modifier, animate = true, content = content)

@Composable
internal fun StudyFocusAction(text: String, glyph: HaloGlyph, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = enabled) =
    HaloFocusAction(uiText(text), glyph, enabled, onClick, modifier, active, animate = true)

/** Finishing an already-open lesson is a moment; restoring a completed lesson is not. */
@Composable
internal fun StudyCompletionMoment(state: StudyUiState) {
    var observedActive by androidx.compose.runtime.remember(state.session?.id) { androidx.compose.runtime.mutableStateOf(false) }
    var show by androidx.compose.runtime.remember(state.session?.id) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state.loading, state.isFinished, state.session?.id) {
        if (!state.loading && state.session != null) {
            if (!state.isFinished) observedActive = true
            else if (observedActive) { observedActive = false; show = true }
        }
    }
    androidx.compose.runtime.LaunchedEffect(show) {
        if (show) { kotlinx.coroutines.delay(1800); show = false }
    }
    if (show) androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        com.halo.app.ui.fx.HaloFxAnimation(com.halo.app.ui.fx.HaloFxMoment.BLOOM, Modifier.size(64.dp))
        androidx.compose.material3.Text(uiText("Lesson complete. You can still review your saved cards."),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
    }
}
