package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.ColumnScope
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
