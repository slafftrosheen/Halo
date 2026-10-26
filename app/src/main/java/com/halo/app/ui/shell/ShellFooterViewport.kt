package com.halo.app.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Insets are applied by the shell before this viewport is measured.
 * An exceptionally tall IME can leave less room than one composer plus navigation.
 * Keep original hit targets and semantics reachable by scrolling instead of clipping/shrinking them.
 */
@Composable
internal fun ShellFooterViewport(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier) {
        val scroll = rememberScrollState()
        val constrained = if (maxHeight < 240.dp)
            Modifier.statusBarsPadding().heightIn(max = maxHeight).verticalScroll(scroll)
        else Modifier
        // One content call site preserves focused editor/IME and popup state across the threshold.
        Column(Modifier.fillMaxWidth().then(constrained),
            horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}
