package com.halo.app.ui.mini

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.workspace.HaloWorkspaceToggleRow
import com.halo.app.ui.workspace.ToolLookDialog
import com.halo.app.ui.workspace.ToolLookSelections
import com.halo.app.ui.workspace.ToolPageAppearance
import com.halo.app.ui.workspace.rememberToolAppearance
import org.json.JSONObject

internal enum class BubbleSize(val diameter: Int, @StringRes val label: Int) {
    SMALL(48, R.string.alive_mini_small), MEDIUM(60, R.string.alive_mini_medium), LARGE(76, R.string.alive_mini_large)
}

internal enum class BubbleEdge(@StringRes val label: Int) {
    LEFT(R.string.alive_mini_left), RIGHT(R.string.alive_mini_right)
}

internal data class MiniAppearance(
    val size: BubbleSize = BubbleSize.MEDIUM,
    val edge: BubbleEdge = BubbleEdge.RIGHT,
    val hideFullscreen: Boolean = false,
)

internal const val MINI_APPEARANCE_KEY = "mini"

internal fun miniAppearanceOwner(context: Context): ToolPageAppearance<MiniAppearance> {
    val legacyX = context.getSharedPreferences("halo_bubble", Context.MODE_PRIVATE).getFloat("x", 1f)
    val initial = MiniAppearance(edge = if (legacyX < .5f) BubbleEdge.LEFT else BubbleEdge.RIGHT)
    return ToolPageAppearance(context, MINI_APPEARANCE_KEY, initial, { json ->
        MiniAppearance(
            size = BubbleSize.entries.firstOrNull { it.name == json.optString("size") } ?: BubbleSize.MEDIUM,
            edge = BubbleEdge.entries.firstOrNull { it.name == json.optString("edge") } ?: initial.edge,
            hideFullscreen = json.optBoolean("hideFullscreen"),
        )
    }, { look -> JSONObject().put("size", look.size.name).put("edge", look.edge.name)
        .put("hideFullscreen", look.hideFullscreen) })
}

@Composable
internal fun MiniAppearanceEntry(iconOnly: Boolean = false, enabled: Boolean = true) {
    val context = LocalContext.current
    val owner = remember(context.applicationContext) { miniAppearanceOwner(context) }
    var saved by rememberToolAppearance(owner)
    var draft by remember { mutableStateOf<MiniAppearance?>(null) }
    val title = stringResource(R.string.alive_mini_look)
    val current = saved
    if (iconOnly) {
        IconButton(onClick = { draft = saved?.current }, enabled = enabled && current != null,
            modifier = Modifier.size(48.dp)) {
            HaloIcon(HaloGlyph.APPEARANCE, title, Modifier.size(24.dp))
        }
    } else OutlinedButton(onClick = { draft = saved?.current }, enabled = enabled && current != null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(title) }
    val pending = draft
    if (current != null && pending != null) {
        ToolLookDialog(R.string.alive_mini_look, current, pending, { draft = it },
            onApply = {
                saved = owner.apply(current, pending)
                draft = null
            }, onDismiss = { draft = null }, preview = {
                Surface(Modifier.fillMaxWidth().height(92.dp), shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.padding(horizontal = 12.dp), contentAlignment =
                        if (pending.edge == BubbleEdge.LEFT) Alignment.CenterStart else Alignment.CenterEnd) {
                        HaloSignalFace(HaloSignalPresentation(label = "mini halo"), size = pending.size.diameter.dp,
                            contentDescription = title)
                    }
                }
            }) {
            ToolLookSelections(R.string.alive_mini_size, BubbleSize.entries.map { it to it.label }, pending.size,
                onSelect = { draft = pending.copy(size = it) })
            ToolLookSelections(R.string.alive_mini_side, BubbleEdge.entries.map { it to it.label }, pending.edge,
                onSelect = { draft = pending.copy(edge = it) })
            HaloWorkspaceToggleRow(stringResource(R.string.alive_mini_hide), pending.hideFullscreen,
                onCheckedChange = { draft = pending.copy(hideFullscreen = it) }, enabled = Build.VERSION.SDK_INT >= 30)
        }
    }
}
