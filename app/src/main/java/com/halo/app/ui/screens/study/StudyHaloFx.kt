package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import com.halo.app.ui.fx.haloTrace
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.design.HaloFocusFrame
import com.halo.app.ui.icons.HaloGlyph

/** Study selects the attention target; rendering and lifecycle policy are shared. */
@Composable
internal fun StudyFocusFrame(active: Boolean, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) =
    HaloFocusFrame(active, modifier, animate = true, content = content)

/** Only a single safe next step receives Guide Trace.
 * The old focus-frame border remains static when Trace is active, so these
 * two decorators never compete for attention.
 */
internal fun studyGuideTraceEnabled(guide: Boolean, active: Boolean, enabled: Boolean): Boolean =
    guide && active && enabled

@Composable
internal fun StudyFocusAction(text: String, glyph: HaloGlyph, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = enabled,
    guide: Boolean = false,
) {
    val traced = studyGuideTraceEnabled(guide, active, enabled)
    HaloFocusAction(uiText(text), glyph, enabled, onClick,
        modifier = if (traced) modifier.haloTrace(true, MaterialTheme.colorScheme.primary)
            else modifier,
        active = active, animate = !traced)
}
