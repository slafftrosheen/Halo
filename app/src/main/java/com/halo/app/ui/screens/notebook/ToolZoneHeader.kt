package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Compact utility band. Editing gestures and durable action owners stay in their page. */
@Composable
internal fun ToolZoneHeader(title: String, onBack: () -> Unit, enabled: Boolean = true,
    onTitleClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!com.halo.app.ui.presence.LocalShellCarouselBackVisible.current) {
            IconButton(onClick = onBack, enabled = enabled, modifier = Modifier.size(48.dp)) {
                HaloIcon(HaloGlyph.PREVIOUS, uiText("Back"))
            }
        }
        Box(Modifier.weight(1f).heightIn(min = 48.dp).then(
            if (onTitleClick != null) Modifier.clickable(enabled = enabled, onClickLabel = uiText("Rename"), onClick = onTitleClick)
            else Modifier), contentAlignment = Alignment.CenterStart) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Only an explicitly identical enabled page action may replace this utility control. */
@Composable
internal fun ToolZoneHeaderAction(actionId: String, content: @Composable () -> Unit) {
    if (actionId !in com.halo.app.ui.presence.LocalShellCarouselActionIds.current) content()
}
